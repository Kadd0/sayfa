# Third-party notices

Sayfa is licensed under the GNU General Public License v3.0 or later (see [LICENSE](LICENSE)).
It bundles the following works, each under its own license. The full license texts ship inside the app
in [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/).

| Component | Files | Copyright | License |
|---|---|---|---|
| [PDF.js](https://github.com/mozilla/pdf.js) 1.10.100 | `assets/pdf.js`, `assets/pdf.worker.js` | Mozilla Foundation | [Apache-2.0](app/src/main/assets/licenses/pdfjs-Apache-2.0.txt) |
| [Hypher](https://github.com/bramstein/hypher) | `assets/hyph.js` (first part) | © 2011 Bram Stein | [BSD-3-Clause](app/src/main/assets/licenses/hypher-BSD.txt) |
| [US English hyphenation patterns](https://github.com/hyphenation/tex-hyphen) (hyph-utf8) | `assets/hyph.js` (pattern table) | © 1990, 2004, 2005 Gerard D.C. Kuiken | Copying and distribution of this file, with or without modification, are permitted in any medium without royalty provided the copyright notice and this notice are preserved. |
| [Literata](https://github.com/googlefonts/literata) | `assets/font-literata-*.ttf` | © 2017 The Literata Project Authors | [SIL OFL 1.1](app/src/main/assets/licenses/literata-OFL.txt) |
| [Crimson Text](https://github.com/googlefonts/Crimson) | `assets/font-crimson-*.ttf` | © 2010 The Crimson Text Project Authors | [SIL OFL 1.1](app/src/main/assets/licenses/crimson-OFL.txt) |

## Dictionary (`assets/dict.db`)

The offline English → Turkish dictionary is built by [`tools/build_dict.py`](tools/build_dict.py) from two sources.
As a combined work it is distributed under **GPL-3.0-or-later**, like the rest of the app.

- **WikDict en-tr**, by Karl Bartel, with data from [Wiktionary](https://www.wiktionary.org/) via [DBnary](http://kaiko.getalp.org/about-dbnary/).
  Source: <https://www.wikdict.com/>. License: [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)
  (one-way compatible with GPLv3).
- **FreeDict English–Turkish (eng-tur)**, © 1999–2017 by various authors, based on gtksozluk by Mehmet Ali Vardar.
  Source: <https://freedict.org/>. License: [GPL-2.0-or-later](https://www.gnu.org/licenses/old-licenses/gpl-2.0.html).

## Online services

The optional **Cümleyi Çevir** (translate sentence) button sends the selected sentence to Google Translate's
web endpoint (`translate.googleapis.com`), falling back to [MyMemory](https://mymemory.translated.net/).
These services are not part of Sayfa and have their own terms. Nothing else leaves the device.
