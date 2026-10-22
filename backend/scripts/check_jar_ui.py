#!/usr/bin/env python3
"""Check the actual bootable artifact, not Vite's development proxy.

Run after `./mvnw clean package`: the Spring Boot JAR must carry the
already-built browser shell, hashed application bundles, and public imagery.
"""

from pathlib import Path
import re
import sys
from zipfile import ZipFile

jar = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('target/hamco-api-0.0.1-SNAPSHOT.jar')
with ZipFile(jar) as archive:
    names = set(archive.namelist())
    root = 'BOOT-INF/classes/static/'
    index = root + 'index.html'
    assert index in names, 'Spring JAR is missing the React index.html'
    html = archive.read(index).decode()
    assert not re.search(r'<script[^>]+src=["\']https?://', html, re.I), 'remote executable script in HTML'
    assets = [name for name in names if name.startswith(root + 'assets/')]
    assert any(name.endswith('.js') for name in assets), 'Spring JAR is missing Vite JavaScript'
    assert any(name.endswith('.css') for name in assets), 'Spring JAR is missing Vite CSS'
    assert root + 'img/favicon.ico' in names, 'Spring JAR is missing the favicon'
    assert root + 'img/main/hammy1.png' in names, 'Spring JAR is missing the masthead'
    # Referenced build assets must actually exist in the same JAR; a loose
    # index.html alone is not a deployable site.
    for path in re.findall(r'(?:src|href)=["\'](/assets/[^"\']+)', html):
        assert root + path.lstrip('/') in names, f'index references missing {path}'
print('Packaged UI: index, hashed JS/CSS, favicon and masthead present')
