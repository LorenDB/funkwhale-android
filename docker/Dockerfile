FROM debian:stable-slim

ENV ANDROID_COMPILE_SDK=33
ENV SDK_TOOLS_VERSION=32.0.0
ENV ANDROID_CMD_TOOLS=9123335
ENV ANDROID_HOME=/opt/android-sdk-linux
ENV PATH "/opt/android-sdk-linux/cmdline-tools/bin:${PATH}"

RUN apt-get update && apt-get install --yes openjdk-11-jdk wget tar unzip lib32stdc++6 lib32z1 git
RUN mkdir -p /opt/android-sdk-linux && cd /opt \
      && wget -q https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMD_TOOLS}_latest.zip -O android-sdk-tools.zip \
      && unzip -q android-sdk-tools.zip -d ${ANDROID_HOME} \
      && rm -f android-sdk-tools.zip \
      && echo y | sdkmanager --sdk_root=${ANDROID_HOME} "platforms;android-${ANDROID_COMPILE_SDK}" \
      && echo y | sdkmanager --sdk_root=${ANDROID_HOME} "platform-tools" \
      && echo y | sdkmanager --sdk_root=${ANDROID_HOME} "build-tools;${SDK_TOOLS_VERSION}"

