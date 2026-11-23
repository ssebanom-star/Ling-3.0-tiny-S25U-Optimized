# HorizonVM 네트워크 분석 (libhvm_android.so)

주소는 Ghidra 주소(= ELF VA + 0x100000). 소스: decomp/native/src/hvm__UserNet.c, hvm__Bcm4356.c

## 구조
게스트(HOS) → Bcm4356 가상 PCIe Wi-Fi (HorizonVM-WiFi, `HVM_WLAN_NOJOIN`으로 조인 생략) → `hvm::UserNet` (slirp 비슷한 자체 NAT) → 안드로이드 호스트 소켓.
INTERNET 권한은 이 NAT 때문. 앱 자체(Java)는 네트워크 코드가 없음.

| 게스트 쪽 주소 | 의미 |
|---|---|
| 10.0.2.15 | 게스트 IP (on_dhcp 가 DHCP 응답 생성) |
| 10.0.2.2 | "호스트" 게이트웨이. 기본: TCP는 RST, UDP는 53번만 DNS로 중계. `HVM_NET_HOSTACCESS` 가 **설정만 돼 있으면**(값 무관, "0"도) 127.0.0.1 로 매핑 |
| 10.0.2.3 | DNS. 호스트 DNS(UserNet 생성자가 `/etc/resolv.conf` nameserver 를 읽음, 없으면 **8.8.8.8 하드코딩**; 안드로이드엔 해당 파일이 없으므로 보통 8.8.8.8) |
| 127.x.x.x | 게스트가 직접 지정하면 TCP RST / UDP 드롭 (호스트 루프백 접근 차단) |
| 255.255.255.255, 224.0.0.0/4 | UDP 브로드캐스트/멀티캐스트 드롭 |
| 그 외 | 투명 NAT: TCP=`socket(AF_INET,SOCK_STREAM|NONBLOCK)`+connect, UDP=`SOCK_DGRAM|NONBLOCK` 소켓 풀, ICMP=on_icmp |

필터링(도메인/IP 차단) 없음. 닌텐도 서버로의 접속도 그대로 나감 → 실기 키/시리얼로 부팅한 VM이 온라인에 붙으면 밴 위험.
제한: TCP 세션 최대 0x200(512)개 (`on_tcp` 의 `0x1ff < size` 검사), 연결당 수신 대기 버퍼 상한 0x40000.

## 함수 주소
| 함수 | Ghidra 주소 |
|---|---|
| UserNet::UserNet (resolv.conf, 8.8.8.8 기본값) | 009e4c70 |
| UserNet::map_dst (10.0.2.2/10.0.2.3 매핑) | 009e5250 |
| send_eth / send_ip / send_udp / send_tcp | 009e5290 / 009e5404 / 009e5684 / 009e5a04 |
| input / on_arp / on_ip / on_icmp | 009e5e0c / 009e5f78 / 009e6060 / 009e60fc |
| on_tcp / on_udp / on_dhcp | 009e67b4 / 009e6d98 / 009e700c |
| tcp_close / tcp_flush / poll | 009e7530 / 009e76a4 / 009e77d0 |
| Bcm4356::reset (HVM_NET_HOSTACCESS 읽음 → UserNet+0xb4) | 009dcc74 (Bcm4356::reset) |
| gdbstub(127.0.0.1) start | 00dc3c7c |
| tele::start_server (127.0.0.1) | 00de4148 |

## 패치 아이디어 (검증된 것)
- DNS 폴백 8.8.8.8 → 1.1.1.1: `sopatch.py asm <so> 9e4d5c "mov w26, #0x1010101"` (UserNet ctor 안 `mov w26,#0x8080808`)
- 호스트 접근 상시 허용: Bcm4356::reset 의 `pUVar6[0xb4] = (getenv(...)!=0)` 부분을 `1` 로.
- 인터넷 차단(완전 오프라인): `UserNet::on_tcp`/`on_udp` 앞부분을 `ret` 로 (`sopatch.py bytes <so> 9e67b4 c0035fd6`) — 게스트는 링크는 되지만 통신 불가.
- 게이트웨이/게스트 IP 변경: 0xa000202, 0xa000203 상수와 ICMP 응답 경로(on_icmp 의 0xa00020f=10.0.2.15) 와 on_dhcp 의 임대 주소 처리도 함께 확인·수정해야 함 (on_dhcp 의 yiaddr 처리는 미확인).
