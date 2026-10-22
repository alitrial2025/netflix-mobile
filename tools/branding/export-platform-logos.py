"""Export bundled, outlined SVG wordmarks as native vectors (requires fonttools).
No fonts, rasterization, external references, or runtime image downloads.
"""
from pathlib import Path
import json
import xml.etree.ElementTree as ET
from fontTools.svgLib.path import SVGPath, parse_path
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.transformPen import TransformPen

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'assets/platform-logos'
RESOURCES = ROOT / 'app/src/main/res/drawable'
NS = '{http://www.w3.org/2000/svg}'

for entry in json.loads((ASSETS / 'sources.json').read_text()):
    key = entry['key']
    source = ET.parse(ASSETS / entry['source']).getroot()
    if key == 'hotstar':
        source = next(node for node in source.iter(NS + 'svg') if node.get('viewBox') == '0 0 3027 797')
    if any(node.tag.rsplit('}', 1)[-1] in {'image', 'text', 'use', 'script', 'foreignObject'} for node in source.iter()):
        raise ValueError('Logos must contain only self-contained outlined vector geometry')
    paths = []
    if key in {'prime', 'peacock'}:
        for node in source:
            if node.tag != NS + 'path':
                raise ValueError('Unexpected wordmark structure')
            fill = node.get('fill') or next((part.split(':', 1)[1] for part in node.get('style', '').split(';') if part.startswith('fill:')), '#000000')
            if fill.lower() in {'#000000', '#000', '#232f3e', 'black'}:
                fill = '#FFFFFF'
            paths.append((node.get('d'), fill))
    else:
        pen = SVGPathPen(None)
        if key == 'hotstar':
            # The outlined wordmark uses a font-coordinate Y axis on its ancestor group.
            # fontTools applies path transforms, so explicitly preserve this group transform.
            group = next(node for node in source if node.tag == NS + 'g')
            if group.get('transform') != 'translate(0.000000,797.000000) scale(0.100000,-0.100000)':
                raise ValueError('Unexpected Hotstar transform')
            SVGPath.fromstring(ET.tostring(source)).draw(TransformPen(pen, (.1, 0, 0, -.1, 0, 797)))
        else:
            SVGPath.fromstring(ET.tostring(source)).draw(pen)
        paths.append((pen.getCommands(), '#E50914' if key == 'netflix' else '#FFFFFF'))
    bounds = BoundsPen(None)
    for data, _ in paths:
        parse_path(data, bounds)
    x0, y0, x1, y1 = bounds.bounds
    padding = max(x1 - x0, y1 - y0) * .008
    x0 -= padding
    y0 -= padding
    width, height = x1 - x0 + padding, y1 - y0 + padding
    svg = ET.Element('svg', xmlns=NS[1:-1], viewBox=f'{x0} {y0} {width} {height}')
    vector = ET.Element('vector', {'xmlns:android': 'http://schemas.android.com/apk/res/android',
        'android:width': f'{width / height * 26:.4f}dp', 'android:height': '26dp',
        'android:viewportWidth': f'{width:.6f}', 'android:viewportHeight': f'{height:.6f}'})
    for data, fill in paths:
        ET.SubElement(svg, 'path', fill=fill, d=data)
        pen = SVGPathPen(None)
        parse_path(data, TransformPen(pen, (1, 0, 0, 1, -x0, -y0)))
        ET.SubElement(vector, 'path', {'android:fillColor': fill, 'android:pathData': pen.getCommands()})
    (ASSETS / f'{key}-wordmark.svg').write_text(ET.tostring(svg, encoding='unicode') + '\n')
    (RESOURCES / f'ic_platform_{key}.xml').write_text(ET.tostring(vector, encoding='unicode') + '\n')
    entry['aspect'] = width / height
    print(key, f'{width / height:.4f}')
