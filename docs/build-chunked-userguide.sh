#!/bin/sh

mkdir -p userguide/ || exit 1
cd userguide/ || exit 2
rm -f *.html *.php
cp ../*.png .
cp ../*.jpeg .

xsltproc --nonet ../formatting/swiftsh_html_chunked.xsl ../userguide.xml
chmod a+r *.php 

