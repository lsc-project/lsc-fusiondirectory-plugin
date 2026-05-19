%global lsc_min_version		2.2

Name:          lsc-fusiondirectory-plugin
Version:       1.2
Release:       1%{?dist}
Summary:       LSC FusionDirectory plugin
License:       BSD-3-Clause
URL:           https://lsc-project.org
Source0:       https://github.com/lsc-project/%{name}/archive/v%{version}/%{name}-%{version}.tar.gz
BuildArch:     noarch

BuildRequires: jpackage-utils
%if 0%{?fedora} || 0%{?rhel} >=10
BuildRequires: java-devel >= 1:21
BuildRequires: maven
BuildRequires: maven-local
%else
# maven is too old in EL8, no workaround yet...
%if 0%{?el8}
BuildRequires: java-21-devel
BuildRequires: maven
BuildRequires: maven-local
%endif
%if 0%{?el9}
BuildRequires: java-21-devel
BuildRequires: maven-openjdk21
BuildRequires: maven-local-openjdk21
%endif
%endif
Requires:      lsc >= %{lsc_min_version}


%description
This is a FusionDirectory plugin for LSC.


%prep
%setup -q


%build
mvn package


%install
# Jar
mkdir -p %{buildroot}%{_libdir}/lsc
install -m 0644 target/%{name}-%{version}-distribution.jar \
  %{buildroot}%{_libdir}/lsc


%files
%license LICENSE.txt
%doc README.md
%doc etc/dst-service/ etc/src-service/
%{_libdir}/lsc/lsc-fusiondirectory-plugin-%{version}-distribution.jar


%changelog
* Mon May 18 2026 Xavier Bachelot <xavier.bachelot@worteks.com> - 1.2-1
- Initial specfile
