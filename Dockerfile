FROM alpine:3.15
# 이 이미지는 배포할 파일들을 /target 으로 복사해주고 종료하는 역할을 한다.
# /target은 컨테이너를 실행할때 -v 옵션으로 마운트 설정한다. (디렉토리가 기존파일들이 있어도 무방)
# 계속 실행될 목적의 컨테이너 이미지가 아님
# swsong@21.12.03

## 배포할 파일을 정한다 #############
WORKDIR /source
COPY build/libs/* /source
COPY build_desc.txt /source

# 빌드시 복사할 파일리스트를 보여준다.
RUN ls -l /source
WORKDIR /target
VOLUME /target
# bak디렉토리로 이전 버전의 분석기 jar를 옮기고 신규 jar를 복사해 넣는다.
# 마지막에는 최종 디렉토리를 ls 해서 보여준다.
CMD ["/bin/sh", "-c", "mkdir -p bak && mv -f analysis-product-*.jar bak/ && cp -r /source/* . && ls -l /target "]
