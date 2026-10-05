# NasCam — 촬영 즉시 Synology NAS 자동 저장 (Android)

## 동작 방식
CameraX로 촬영 → 앱 전용 폴더에 JPG 저장 → WorkManager가 Synology **WebDAV**로 PUT 업로드.
네트워크 끊김/Wi-Fi 아님 상태면 대기했다가 조건 충족 시 자동 업로드(지수 백오프 재시도).

## NAS 준비
1. DSM 패키지 센터 → **WebDAV Server** 설치 → HTTP(5005) 또는 HTTPS(5006) 활성화
2. 사진 저장용 공유폴더(예: `photo`)에 해당 계정 읽기/쓰기 권한 부여
3. 외부에서 쓸 경우: DDNS + HTTPS(5006) + Let's Encrypt 인증서 권장 (QuickConnect는 WebDAV 미지원)

## 앱 설정 예시
- 서버 URL: `http://192.168.0.10:5005` (LAN) / `https://xxx.synology.me:5006`
- 저장 폴더: `photo/NasCam`
- 사용자/비밀번호: DSM 계정

## 빌드
Android Studio (Ladybug 이상)에서 폴더 열기 → Sync → Run.
minSdk 26, targetSdk 35, Kotlin 2.0, AGP 8.7.

## 구조
```
app/src/main/java/kr/re/kitech/nascam/
  MainActivity.kt     카메라 미리보기·촬영·상태표시
  SettingsActivity.kt NAS 접속정보 입력
  UploadWorker.kt     WebDAV MKCOL + PUT 업로드 (재시도 포함)
  Prefs.kt            설정 저장
```

## 공유(보내기)로 NAS 저장
갤러리·카카오톡·파일 앱 등에서 **공유 → "NAS에 저장"** 을 선택하면 선택한 파일들이
설정된 WebDAV 폴더(날짜 하위폴더 옵션 적용)로 백그라운드 업로드됩니다.
이미지·동영상·PDF·문서 등 모든 파일 형식과, 텍스트/링크 공유(.txt로 저장)를 지원합니다.
