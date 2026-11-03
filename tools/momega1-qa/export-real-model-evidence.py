#!/usr/bin/env python3
"""Copy synthetic acceptance artifacts, using the recorded key-excluding inventory."""
import hashlib,json,pathlib,shutil,sys
source,destination=map(pathlib.Path,sys.argv[1:3])
destination.mkdir(parents=True,exist_ok=False)
for folder in [source,source/'task']:
    for item in folder.iterdir():
        if item.is_file() and not item.is_symlink():
            target=destination/item.relative_to(source)
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(item,target)
state=source/'task/private-synthetic-state'
for item in json.loads((source/'task/state-evidence-inventory.json').read_text()):
    relative=pathlib.Path(item['path'])
    assert not relative.is_absolute() and '..' not in relative.parts
    assert all(not part.startswith('.') for part in relative.parts)
    assert not any(token in relative.name.lower() for token in ['key','secret','credential'])
    original=state/relative
    assert original.resolve()==original and original.is_file()
    assert hashlib.sha256(original.read_bytes()).hexdigest()==item['sha256']
    target=destination/'public-synthetic-state-evidence'/relative
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(original,target)
manifest={str(p.relative_to(destination)):hashlib.sha256(p.read_bytes()).hexdigest()
          for p in sorted(destination.rglob('*')) if p.is_file()}
(destination/'export-sha256.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'exported':str(destination),'files':len(manifest),'privateKeysExported':False}))
