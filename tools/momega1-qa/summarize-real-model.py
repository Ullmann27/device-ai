#!/usr/bin/env python3
"""Audit completed synthetic benchmark receipts; never promotes driver exit to success."""
import hashlib,json,pathlib,sys
root=pathlib.Path(sys.argv[1]);output=pathlib.Path(sys.argv[2]);rows=[]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for index in range(1,13):
    name=f'u{index:02d}';folder=root/name;task=folder/'task'
    if not (task/'outcome.json').exists():
        rows.append({'task':name,'status':'NOT_RUN_OR_INCOMPLETE'});continue
    before=json.loads((task/'before.json').read_text());after=json.loads((task/'after.json').read_text())
    outcome=json.loads((task/'outcome.json').read_text())
    result=json.loads((task/'result.json').read_text()) if (task/'result.json').exists() else {}
    state=task/'private-synthetic-state';inventory=json.loads((task/'state-evidence-inventory.json').read_text())
    for item in inventory:
        assert sha(state/item['path'])==item['sha256'],item['path']
    requests=json.loads((task/'model-requests.json').read_text())
    reports=list((state/'teamleader/reports/owner-repair').glob('*/report.json'))
    report=json.loads(reports[0].read_text()) if len(reports)==1 else {}
    verified=False;independent_proofs=[]
    for receipt in result.get('receipts',[]):
        if receipt.get('status')!='REPAIR_PATCH_VERIFIED_PENDING_REVIEW':continue
        patch=pathlib.Path(receipt['patch']['path']);assert sha(patch)==receipt['patch']['sha256']
        assert receipt['evidence']['evidenceSufficient'] is True
        assert report['verified']['patchSha256']==receipt['patch']['sha256']
        assert report['verified']['files']==['lib/subject.mjs']
        directory=patch.parent
        checks=list(directory.glob('candidate-independent-*/verification.json'))
        matching=[]
        for file in checks:
            v=json.loads(file.read_text())
            if v['verdict']=='VERIFIED' and v['patchSha256']==receipt['patch']['sha256']:
                assert v['sourceCommit']==result['sourceCommit']
                assert v['red']['passed'] is False and v['green']['passed'] is True
                assert any(r.get('modelObservation',{}).get('proposalSha256')==v['proposalSha256'] and r.get('verifiedModelExecution') is True for r in requests)
                matching.append({'path':str(file.relative_to(state)),'sha256':sha(file)})
        assert matching,'Missing independent model-bound verification'
        final=directory/'independent-check/verification.json';v=json.loads(final.read_text())
        assert v['passed'] is True and v['evidenceSufficient'] is True and v['executedTests']>0
        assert v['head']==result['sourceCommit'] and v['patchSha256']==receipt['patch']['sha256']
        independent_proofs=matching+[{'path':str(final.relative_to(state)),'sha256':sha(final)}]
        verified=True
    assert outcome['sourceIdentityStable'] is True
    assert before['integratedSource']==after['integratedSource']=='19474261c15a3503a9f041244b4b4c3eaed4636f'
    assert before['fixture']['materializedTestSha256']==after['testSha256']
    resource=json.loads((folder/'native-resources.json').read_text())
    recall_lines=(task/'restart-recall.stdout').read_text().splitlines() if (task/'restart-recall.stdout').exists() else []
    recall=json.loads(recall_lines[-1]) if recall_lines and recall_lines[-1].startswith('{') else {}
    native=resource['processes']
    rows.append({'task':name,'status':result.get('status',outcome['status']),
        'independentRepairVerified':verified,'sourceCommit':before['fixture']['task']['sourceCommit'],
        'modelCallsReserved':len(requests),'modelExecution':result.get('modelExecution'),
        'modelRequests':[{'request':r['request'],'status':r['status'],'failureKind':r.get('failureKind'),
            'elapsedMs':r.get('elapsedMs'),'outputTokens':r.get('modelObservation',{}).get('outputTokens'),
            'promptSha256':r.get('promptSha256'),'responseSha256':r.get('responseSha256')} for r in requests],
        'testedCandidates':len(report.get('attempts',[])),'rejections':report.get('inference',{}).get('rejections',[]),
        'taskElapsedMs':after['elapsedMs'],'technicalExperienceStatus':result.get('technicalExperienceStatus'),
        'restartRecallStatus':recall.get('status'),'restartRecallItems':len(recall.get('items',[])),
        'independentProofs':independent_proofs,'sourceAndTestIdentityStable':True,
        'nativeProcesses':len(native),'peakObservedNativeRssKiB':max((r['rssKiB'] for r in native if 'rssKiB' in r),default=None),
        'peakObservedNativePssKiB':max((r['pssKiB'] for r in native if 'pssKiB' in r),default=None),
        'observedNativeCpuSeconds':sum(r['observedCpuSeconds'] for r in native if 'observedCpuSeconds' in r) if any('observedCpuSeconds' in r for r in native) else None})
summary={'schema':'momega1-original-real-model-acceptance-summary-v1','source':'19474261c15a3503a9f041244b4b4c3eaed4636f',
    'tasks':rows,'completed':sum(r['status']!='NOT_RUN_OR_INCOMPLETE' for r in rows),
    'independentlyVerifiedRepairs':sum(r.get('independentRepairVerified',False) for r in rows),
    'successfulRestartRecalls':sum(r.get('restartRecallItems',0)>0 for r in rows),
    'scope':'Original twelve frozen tasks only. Later diagnostic reruns or paired experiment are excluded. No merge/device/install/weight learning claim.'}
output.write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({k:v for k,v in summary.items() if k!='tasks'}))
