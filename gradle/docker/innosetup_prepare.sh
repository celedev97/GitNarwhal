# Install innoextract
sudo apt-get update
sudo apt-get install -y software-properties-common
sudo add-apt-repository ppa:arx/release -y
sudo apt-get update
sudo apt-get install -y innoextract

# Install wget
sudo apt-get install -y wget

# Install wine
sudo apt-get install -y wine-stable
sudo dpkg --add-architecture i386
sudo apt-get update
sudo apt-get install wine32 -y

# Install innosetup in C:\inno
wget -O is.exe https://files.jrsoftware.org/is/6/innosetup-6.0.2.exe
innoextract is.exe
winecfg
mkdir -p ~/.wine/drive_c/inno
cp -a app/* ~/.wine/drive_c/inno

winecfg