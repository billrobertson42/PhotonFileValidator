; Script generated by the Inno Setup Script Wizard.
; SEE THE DOCUMENTATION FOR DETAILS ON CREATING INNO SETUP SCRIPT FILES!

#define MyAppName "Photon File Validator"
#define MyAppVersion "1.2"
#define MyAppPublisher "Bonosoft"
#define MyAppURL "https://github.com/Photonsters/PhotonFileValidator"
#define MyAppExeName "photonfilevalidator.exe"

[Setup]
; NOTE: The value of AppId uniquely identifies this application.
; Do not use the same AppId value in installers for other applications.
; (To generate a new GUID, click Tools | Generate GUID inside the IDE.)
AppId={{63A4875F-C11F-4FF1-B724-539ECD089388}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
;AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={pf}\{#MyAppName}
DisableProgramGroupPage=yes
OutputDir=D:\Users\bn\Documents\GitHub\PhotonFileViewer\release
OutputBaseFilename=win-photonfilevalidator-{#MyAppVersion}
SetupIconFile=D:\Users\bn\Documents\GitHub\PhotonFileViewer\install\img\icons\photonsters.ico
Compression=lzma
SolidCompression=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "D:\Users\bn\Documents\GitHub\PhotonFileViewer\out\win\photonfilevalidator.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "D:\Users\bn\Documents\GitHub\PhotonFileViewer\out\win\photon.properties"; DestDir: "{app}"; Flags: ignoreversion
; NOTE: Don't use "Flags: ignoreversion" on any shared system files

[Icons]
Name: "{commonprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{commondesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

