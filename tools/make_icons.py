"""Генерирует все файлы логотипа Imast из одного описания (эскиз Марка, доска 7, буквы по центру).
Запуск: python3 tools/make_icons.py . /tmp/icons  (нужен rsvg-convert)."""
import os, subprocess, sys

REPO = sys.argv[1]
OUT = sys.argv[2]
os.makedirs(OUT, exist_ok=True)

GOLD, INK, TILE, BG = '#D4AE5A', '#F4E4B0', '#14203A', '#0E1526'
LI = ('M21.25 24.6 H32.08 V36.25 C34 31 38.6 27.9 44.2 27.9 C48.8 27.9 51.8 30.6 51.8 35.2 V42.1 H54.8 V43.75 H45.2 V35 '
      'C45.2 32.8 43.8 31.6 41.7 31.6 C37.8 31.6 34 34.6 32.08 39.6 V73.3 H36.7 V75.8 H25 V73.6 L25.67 73.3 V26.25 H21.8 Z')
LM1 = ('M42.5 45.9 H50.8 V71.4 C50.8 72.4 51.4 73.2 52.5 73.1 L63.8 66.75 L63.8 69.6 L48.8 76.75 '
       'C46.4 76.75 44.5 75.6 44.5 73.4 V48.1 C44.5 47.6 43.5 47.3 42.5 47.4 Z')
LM2 = ('M63.8 75.75 H72.5 V73.4 H70.2 V44.1 C69.4 42 66.4 40 65 38.2 C63.6 36.4 63.6 33.8 64.8 32.8 '
       'C66 31.8 68.4 32 69.4 33.8 L70 35.6 L71.9 33.75 C71.6 30 68.6 27.5 65 27.5 C61 27.5 58.6 30.8 58.75 34.6 '
       'C58.9 38.2 61.4 41 63.8 42.75 Z')
STAR = 'M80.4 11.7 L83.3 18.3 L90 21.25 L83.2 24.4 L80.6 30.4 L77.7 24.4 L70.6 21.25 L77.6 18.2 Z'
TICKS = ['M50 4 V7', 'M50 93 V96', 'M4 50 H7', 'M93 50 H96', 'M17.47 17.47 L19.6 19.6',
         'M17.47 82.53 L19.6 80.4', 'M82.53 82.53 L80.4 80.4']
SHIFT = (3.125, -0.675)  # буквы по центру кольца
LETTERS = (LI, LM1, LM2)


def letters_svg(fill, bold=0.0):
    st = f' stroke="{fill}" stroke-width="{bold}" stroke-linejoin="round"' if bold else ''
    return (f'<g transform="translate({SHIFT[0]} {SHIFT[1]})" fill="{fill}"{st}>'
            + ''.join(f'<path d="{d}"/>' for d in LETTERS) + '</g>')


def full_mark(gold=GOLD, ink=INK, cut=BG):
    """Полный знак: двойное кольцо, деления, звезда — для заставки и крупных размеров."""
    return (f'<g fill="none" stroke="{gold}" stroke-width="1"><circle cx="50" cy="50" r="46"/><circle cx="50" cy="50" r="43"/>'
            + ''.join(f'<path d="{d}"/>' for d in TICKS) + '</g>'
            + f'<path d="{STAR}" fill="{gold}" stroke="{cut}" stroke-width="0.9" stroke-linejoin="round"/>'
            + letters_svg(ink))


def small_mark(gold=GOLD, ink=INK, cut=TILE, ring=3.6, bold=1.0):
    """Упрощённый знак для иконок: одно кольцо, без делений, буквы чуть жирнее."""
    return (f'<circle cx="50" cy="50" r="44" fill="none" stroke="{gold}" stroke-width="{ring}"/>'
            + f'<path d="{STAR}" fill="{gold}" stroke="{cut}" stroke-width="1.4" stroke-linejoin="round"/>'
            + letters_svg(ink, bold))


def svg(view, body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}">{body}</svg>'


def render(svg_text, name, size):
    src = os.path.join(OUT, name + '.svg')
    open(src, 'w').write(svg_text)
    png = os.path.join(OUT, f'{name}-{size}.png')
    subprocess.run(['rsvg-convert', '-w', str(size), '-h', str(size), src, '-o', png], check=True)
    return png


# 1. Android: адаптивная иконка. Знак (радиус со звездой ~49.5 ед.) * 0.64 = ~32 dp — в безопасной зоне 66 dp.
S = 0.64
def vec_path(d, **a):
    attrs = ' '.join(f'android:{k}="{v}"' for k, v in a.items())
    return f'        <path android:pathData="{d}" {attrs} />\n'

RING = 'M6,50 A44,44 0 1,0 94,50 A44,44 0 1,0 6,50 Z'

def vector(mono=False):
    g = '#FFFFFFFF' if mono else GOLD
    i = '#FFFFFFFF' if mono else INK
    out = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- Логотип Imast: Իմ в кольце со звездой (эскиз Марка). Генерируется скриптом, размер 108 dp, знак в безопасной зоне 66 dp. -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="108dp" android:height="108dp"\n'
           '    android:viewportWidth="108" android:viewportHeight="108">\n'
           f'    <group android:pivotX="50" android:pivotY="50" android:scaleX="{S}" android:scaleY="{S}"\n'
           '        android:translateX="4" android:translateY="4">\n')
    out += vec_path(RING, fillColor='#00000000', strokeColor=g, strokeWidth='3.6')
    if mono:
        out += vec_path(STAR, fillColor=g)
    else:
        out += vec_path(STAR, fillColor=g, strokeColor=TILE, strokeWidth='1.4', strokeLineJoin='round')
    out += f'        <group android:translateX="{SHIFT[0]}" android:translateY="{SHIFT[1]}">\n'
    for d in LETTERS:
        out += '    ' + vec_path(d, fillColor=i, strokeColor=i, strokeWidth='1.0', strokeLineJoin='round')
    out += '        </group>\n    </group>\n</vector>\n'
    return out

res = os.path.join(REPO, 'app/src/main/res')
open(os.path.join(res, 'drawable/ic_launcher_fg.xml'), 'w').write(vector())
open(os.path.join(res, 'drawable/ic_launcher_mono.xml'), 'w').write(vector(mono=True))

# Тот же знак в SVG, как его рисует Android (108 dp), — для превью с масками.
adaptive_fg = f'<g transform="translate(54 54) scale({S}) translate(-50 -50)">' + small_mark() + '</g>'

# 2. Старые Android (7.x): PNG — плитка со скруглёнными углами.
legacy = svg('0 0 48 48', f'<rect x="2" y="2" width="44" height="44" rx="9" fill="{TILE}"/>'
             '<g transform="translate(24 24) scale(0.40) translate(-50 -50)">' + small_mark() + '</g>')
for folder, px in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    p = render(legacy, 'legacy', px)
    subprocess.run(['cp', p, os.path.join(res, f'mipmap-{folder}/ic_launcher.png')], check=True)

# 3. Google Play: 512×512, полный квадрат (углы скругляет сам Play), тот же макет, что у адаптивной иконки.
play = svg('18 18 72 72', f'<rect width="108" height="108" fill="{TILE}"/>' + adaptive_fg)
os.makedirs(os.path.join(REPO, 'store'), exist_ok=True)
subprocess.run(['cp', render(play, 'play', 512), os.path.join(REPO, 'store/icon-512.png')], check=True)

# 4. Веб: значок вкладки.
fav = svg('0 0 100 100', f'<rect width="100" height="100" rx="22" fill="{TILE}"/>'
          '<g transform="translate(50 50) scale(0.92) translate(-50 -50)">' + small_mark(ring=5, bold=1.4) + '</g>')
open(os.path.join(REPO, 'app/src/main/assets/icon.svg'), 'w').write(fav + '\n')

# 5. Превью для Марка: маски лаунчеров и реальные размеры.
masks = {'circle': '<circle cx="54" cy="54" r="36"/>',
         'squircle': '<rect x="18" y="18" width="72" height="72" rx="26"/>',
         'rounded': '<rect x="18" y="18" width="72" height="72" rx="14"/>'}
for k, m in masks.items():
    s = svg('18 18 72 72', f'<defs><clipPath id="c">{m}</clipPath></defs><g clip-path="url(#c)">'
            f'<rect width="108" height="108" fill="{TILE}"/>{adaptive_fg}</g>')
    render(s, 'mask-' + k, 432)
render(svg('0 0 100 100', full_mark()), 'splash-mark', 600)
print('ok')
