#!/usr/bin/env python3
"""Summarize actual JUnit XML with complete case identity and skip/failure detail."""
import json,sys,hashlib
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(sys.argv[1]);rows=[];files=[]
for path in sorted(root.rglob('TEST-*.xml')):
    files.append({'path':str(path.relative_to(root)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    document=ET.parse(path).getroot()
    for case in document.iter('testcase'):
        failure=case.find('failure');error=case.find('error');skip=case.find('skipped')
        status='FAIL' if failure is not None or error is not None else 'SKIP' if skip is not None else 'PASS'
        detail=failure if failure is not None else error if error is not None else skip
        rows.append({'class':case.get('classname',''),'name':case.get('name',''),'status':status,
                     'detail':None if detail is None else detail.get('message') or detail.text,
                     'file':str(path.relative_to(root))})
report={'total':len(rows),'pass':sum(x['status']=='PASS' for x in rows),'fail':sum(x['status']=='FAIL' for x in rows),'skip':sum(x['status']=='SKIP' for x in rows),'files':files,'cases':rows}
print(json.dumps(report,indent=2))
if not rows:sys.exit(2)
if report['fail']:sys.exit(1)
