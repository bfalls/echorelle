"""Extract enclosed panes from generated art's dark leading; no runtime image analysis.

Run with Pillow/numpy: python tools/stained-glass/prepare.py INPUT_DIR
Original PNGs are retained in ignored device-results/glass-research.
"""
from pathlib import Path
from collections import deque
import json
import sys
import numpy as np
from PIL import Image, ImageFilter

OUT = Path(__file__).resolve().parents[2] / 'apps/android/app/src/main/assets/glass'

def simplify(points, tolerance=.7):
    if len(points)<3: return points
    a=np.array(points[0]); b=np.array(points[-1]); v=b-a
    q=np.array(points); length=float(v@v)
    t=np.clip(((q-a)@v)/max(length,1e-8),0,1)
    distances=np.sum((q-a-t[:,None]*v)**2,axis=1)
    k=int(np.argmax(distances))
    if distances[k]<=tolerance*tolerance:return [points[0],points[-1]]
    return simplify(points[:k+1],tolerance)[:-1]+simplify(points[k:],tolerance)

for name in ['blossom','iris','garden']:
    image=Image.open(Path(sys.argv[1])/f'{name}.png').convert('RGB')
    image.resize((1024,683),Image.Resampling.LANCZOS).save(OUT/f'{name}.webp',quality=90)
    small=image.resize((768,512),Image.Resampling.LANCZOS)
    # Close tiny breaks in dark leading. Bright pane interiors become connected components.
    lead=small.convert('L').point(lambda p:255 if p<62 else 0).filter(ImageFilter.MaxFilter(3))
    mask=np.array(lead)==0; labels=np.zeros(mask.shape,dtype=np.int32); regions=[]
    height,width=mask.shape
    for y in range(height):
        for x in range(width):
            if not mask[y,x] or labels[y,x]:continue
            label=len(regions)+1; queue=deque([(x,y)]); labels[y,x]=label; region=[]
            while queue:
                xx,yy=queue.popleft();region.append((xx,yy))
                for nx,ny in [(xx-1,yy),(xx+1,yy),(xx,yy-1),(xx,yy+1)]:
                    if 0<=nx<width and 0<=ny<height and mask[ny,nx] and labels[ny,nx]==0:
                        labels[ny,nx]=label;queue.append((nx,ny))
            regions.append(region)
    # Grow the retained interiors through lead/dark glass. This assigns every image pixel
    # exactly once, keeps dark colored glass, and makes adjacent silhouettes meet at seams.
    retained={i+1 for i,r in enumerate(regions) if len(r)>=45}
    labels[~np.isin(labels,list(retained))]=0
    queue=deque((x,y) for y,x in zip(*np.nonzero(labels)))
    while queue:
        x,y=queue.popleft()
        for nx,ny in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]:
            if 0<=nx<width and 0<=ny<height and labels[ny,nx]==0:
                labels[ny,nx]=labels[y,x];queue.append((nx,ny))
    expanded={label:[] for label in retained}
    for y in range(height):
        for x in range(width):expanded[labels[y,x]].append((x,y))
    panes=[]
    for label,region in expanded.items():
        # Clockwise pixel-boundary edges; keep the outer contour, not tiny grain holes.
        edges={}
        def different(x,y):return x<0 or x>=width or y<0 or y>=height or labels[y,x]!=label
        for x,y in region:
            if different(x,y-1):edges[(x,y)]=(x+1,y)
            if different(x+1,y):edges[(x+1,y)]=(x+1,y+1)
            if different(x,y+1):edges[(x+1,y+1)]=(x,y+1)
            if different(x-1,y):edges[(x,y+1)]=(x,y)
        loops=[]
        while edges:
            start=next(iter(edges));p=start;loop=[]
            while p in edges:
                loop.append(p);p=edges.pop(p)
                if p==start:break
            if len(loop)>3:loops.append(loop)
        if not loops:continue
        loop=max(loops,key=len);half=len(loop)//2
        points=simplify(loop[:half+1])[:-1]+simplify(loop[half:]+[loop[0]])[:-1]
        if len(points)>=3:panes.append([[round(x/width,6),round(y/height,6)] for x,y in points])
    (OUT/f'{name}.json').write_text(json.dumps({'version':1,'panes':panes},separators=(',',':')))
    print(name,len(panes),'panes',sum(len(p) for p in panes),'vertices')
