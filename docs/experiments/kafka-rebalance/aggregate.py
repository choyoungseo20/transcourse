import subprocess, sys, statistics
import os
S=os.path.dirname(os.path.abspath(__file__))+'/results'
PW=[l.split('=',1)[1].strip() for l in open(os.path.join(S,'..','..','..','..','.env')) if l.startswith('MYSQL_ROOT_PASSWORD=')][0]
def q(sql):
    out=subprocess.run(['docker','exec','transcourse-mysql-1','mysql','-uroot','-p'+PW,'transcourse','-N','-B','-e',sql],capture_output=True,text=True).stdout
    return [l.split('\t') for l in out.split('\n') if l.strip() and 'Using a password' not in l]
rows=[]
for line in open(f'{S}/runs.txt'):
    label,t0,ts,te,first=line.strip().split('|')
    # 증설 전후의 소유자 비교로 이동 파티션 계산
    before={}
    for mem,parts in q(f"select member_id,partitions from rebalance_event where type='ASSIGNED' and occurred_at<'{ts}' order by id"):
        for p in parts.split(','):
            if p: before[p]=mem
    ev=q(f"select type,member_id,partitions from rebalance_event where occurred_at between '{ts}' and date_add('{ts}', interval 90 second) order by id")
    revoked=set(); after=dict(before)
    for typ,mem,parts in ev:
        for p in parts.split(','):
            if not p: continue
            if typ=='REVOKED': revoked.add(p); after.pop(p,None)
            elif typ=='ASSIGNED': after[p]=mem
    moved=[p for p in after if before.get(p)!=after[p]]
    dup=q(f"""select e.partition_no,e.record_offset,e.result,round(timestampdiff(microsecond,e.received_at,e.finished_at)/1e6,1)
        from worker_execution e join processing_job j on j.id=e.job_id where j.type='TRANSCODING' and e.received_at>='{t0}' and e.received_at<'{te}'
        and (e.partition_no,e.record_offset) in (select partition_no,record_offset from worker_execution where received_at>='{t0}' and received_at<'{te}' group by partition_no,record_offset having count(*)>1)""")
    rejected=[float(d[3]) for d in dup if d[2]=='RESULT_REJECTED']
    kept_skips=[d for d in dup if d[2]=='SKIPPED']
    busy=q(f"select count(distinct e.partition_no) from worker_execution e join processing_job j on j.id=e.job_id where j.type='TRANSCODING' and e.received_at<'{ts}' and e.finished_at>'{ts}'")[0][0]
    enc,total=q(f"select round(avg(timestampdiff(microsecond,e.received_at,e.finished_at))/1e6), round(sum(timestampdiff(microsecond,e.received_at,e.finished_at))/1e6,1) from worker_execution e join processing_job j on j.id=e.job_id where j.type='TRANSCODING' and e.result in ('SUCCEEDED','RESULT_REJECTED') and e.received_at>='{t0}' and e.received_at<'{te}'")[0]
    wasted=round(sum(rejected),1)
    rows.append(dict(label=label,busy=int(busy),revoked=len(revoked),moved=len(moved),dup=len(rejected),wasted=wasted,wasted_pct=round(wasted/float(total)*100,1),kept_skips=len(kept_skips),avg_enc=enc))
print(f"{'run':16}{'busy':>5}{'revoked':>8}{'moved':>6}{'dup':>4}{'wasted_s':>9}{'wasted_%':>9}{'kept_skip':>10}{'avg_enc':>8}")
for r in rows: print(f"{r['label']:16}{r['busy']:>5}{r['revoked']:>8}{r['moved']:>6}{r['dup']:>4}{r['wasted']:>9}{r['wasted_pct']:>9}{r['kept_skips']:>10}{r['avg_enc']:>8}")
for strat in ('range','cooperative'):
    g=[r for r in rows if r['label'].split('-')[0]==strat]
    if not g: continue
    m=lambda k: round(statistics.mean(r[k] for r in g),1)
    print(f"{strat+' avg':16}{m('busy'):>5}{m('revoked'):>8}{m('moved'):>6}{m('dup'):>4}{m('wasted'):>9}{m('wasted_pct'):>9}{m('kept_skips'):>10}  n={len(g)}")
