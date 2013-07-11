Summary: A utility for patching ELF binaries

Name: patchelf
Version: 0.5
Release: 1
License: GPL
Group: Development/Tools
URL: http://nixos.org/patchelf.html
Source0: %{name}-0.5.tar.bz2
BuildRoot: %{_tmppath}/%{name}-%{version}-buildroot
Prefix: /usr

%description

PatchELF is a simple utility for modifing existing ELF executables and
libraries.  It can change the dynamic loader ("ELF interpreter") of
executables and change the RPATH of executables and libraries.

%prep
%setup -q

%build
./configure --prefix=%{_prefix}
make
make check

%install
rm -rf $RPM_BUILD_ROOT
make DESTDIR=$RPM_BUILD_ROOT install
strip $RPM_BUILD_ROOT/%{_prefix}/bin/* || true

%clean
rm -rf $RPM_BUILD_ROOT

%files
/