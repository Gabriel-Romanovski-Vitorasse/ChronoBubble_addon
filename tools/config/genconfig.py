"""Gera src/main/java/.../ChronoConfig.java e as chaves config.chrono_bubble.* dos arquivos de idioma.

Uso (na raiz do reposit\u00f3rio):
    python3 tools/config/genconfig.py src/main/java/com/gabriel/chronobubble/ChronoConfig.java src/main/resources/assets/chrono_bubble/lang

Para criar ou mudar uma op\u00e7\u00e3o, edite tools/config/opts.py (nome, padr\u00e3o, limites, textos pt_br/en_us) e rode de novo.
N\u00e3o edite ChronoConfig.java \u00e0 m\u00e3o: ele \u00e9 sobrescrito.
"""
import os, sys, json, collections
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from opts import SECTIONS
def jesc(s):
    out=''
    for ch in s:
        if ord(ch)>127: out+='\\u%04x'%ord(ch)
        elif ch=='"': out+='\\"'
        else: out+=ch
    return out
def lit(kind, v):
    if kind=='bool': return 'true' if v else 'false'
    if kind=='int': return str(int(v))
    return repr(float(v))
TYPES={'bool':'ForgeConfigSpec.BooleanValue','int':'ForgeConfigSpec.IntValue','double':'ForgeConfigSpec.DoubleValue'}
fields=[]; body=[]
for side in ('server','client'):
    var='s' if side=='server' else 'c'
    body.append(f'        ForgeConfigSpec.Builder {var} = new ForgeConfigSpec.Builder();')
    for (sec, sside, ptt, ent, ptc, enc, opts) in SECTIONS:
        if sside!=side: continue
        body.append(f'')
        body.append(f'        section({var}, "{sec}", {str(side=="server").lower()}, "{jesc(ptc)}");')
        for (key, F, kind, d, mn, mx, pl, el, ptip, etip) in opts:
            fields.append(f'    public static final {TYPES[kind]} {F};')
            if kind=='bool':
                body.append(f'        {F} = bool({var}, "{key}", "{jesc(ptip)}", {lit(kind,d)});')
            elif kind=='int':
                body.append(f'        {F} = integer({var}, "{key}", "{jesc(ptip)}", {lit(kind,d)}, {int(mn)}, {int(mx)});')
            else:
                body.append(f'        {F} = dbl({var}, "{key}", "{jesc(ptip)}", {lit(kind,d)}, {float(mn)!r}, {float(mx)!r});')
        body.append(f'        {var}.pop();')
    body.append(f'        {"SERVER_SPEC" if side=="server" else "CLIENT_SPEC"} = {var}.build();')
    body.append('')
java = open(os.path.join(HERE, 'ChronoConfig.template')).read().replace('%FIELDS%','\n'.join(fields)).replace('%BODY%','\n'.join(body).rstrip())
open(sys.argv[1],'w').write(java)
# lang
for lang, li, ci, ti in (('pt_br',2,6,8),('en_us',3,7,9)):
    path=f'{sys.argv[2]}/{lang}.json'
    d=json.load(open(path), object_pairs_hook=collections.OrderedDict)
    for sec in SECTIONS:
        d[f'config.chrono_bubble.{sec[0]}']=sec[li]
        for o in sec[6]:
            d[f'config.chrono_bubble.{sec[0]}.{o[0]}']=o[ci]
            d[f'config.chrono_bubble.{sec[0]}.{o[0]}.tooltip']=o[ti]
    open(path,'w').write(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
