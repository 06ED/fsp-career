let
  pkgs = import (builtins.getFlake "nixpkgs").outPath {};
in [
  (pkgs.python3.withPackages (p: [ p.python-pptx p.pillow p.qrcode ]))
  pkgs.libreoffice
  pkgs.fontconfig
  pkgs.poppler-utils
]
