#fcdev/gitnarwhal:win
FROM adoptopenjdk:13-jdk-hotspot-bionic

# Install innoextract
RUN apt-get update
RUN apt-get install -y software-properties-common
RUN add-apt-repository ppa:arx/release -y
RUN apt-get update
RUN apt-get install -y innoextract

# Install wget
RUN apt-get install -y wget

# Install wine
RUN apt-get install -y wine-stable
RUN dpkg --add-architecture i386
RUN apt-get update
RUN apt-get install wine32 -y

# Install innosetup in C:\inno
RUN wget -O is.exe https://files.jrsoftware.org/is/6/innosetup-6.0.2.exe
RUN innoextract is.exe
RUN winecfg
RUN mkdir -p ~/".wine/drive_c/inno"
RUN cp -a app/* ~/".wine/drive_c/inno"

RUN winecfg