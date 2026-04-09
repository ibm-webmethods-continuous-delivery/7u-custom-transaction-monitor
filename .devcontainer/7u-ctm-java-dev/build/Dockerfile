ARG __msr_image="ibmwebmethods.azurecr.io/webmethods-microservicesruntime:11.1.0.9"
ARG __alpine_java_dev_image="iwcd-min-java-dev-s:alpine"

FROM ${__msr_image} AS msr

# Build this using https://github.com/ibm-webmethods-continuous-delivery/7u-container-images/tree/main/images/s/alpine/min-java-dev
FROM ${__alpine_java_dev_image}

ARG __gid=1001
ARG __gname=vscode
ARG __uid=1001
ARG __uname=vscode

# this folder receives a mount of the main repository
ARG __workspace_home=/work

# this folder is expected to receive a mount for the user home
ARG __user_home=/home/${__uname}
ARG __supplemental_cp_dir=/cp

ENV 7U_CTM_USER_HOME=${__user_home} \
    7U_CTM_WORKSPACE=${__workspace_home}

RUN apk --no-cache add openjdk21 \
    && addgroup -g ${__gid} ${__gname} \
    && mkdir -p \
      "${__supplemental_cp_dir}" \
      "${__user_home}" \
      "${__workspace_home}" \
    && adduser -u ${__uid} -G ${__gname} -D -h ${__user_home} ${__uname} \
    && rm -rf /tmp/* \
    && chown -R ${__uid}:${__gid} \
      "${__supplemental_cp_dir}" \
      "${__workspace_home}" \
      /tmp
USER ${__uname}

COPY --from=msr /opt/softwareag/common/lib/wm-isclient.jar "${__supplemental_cp_dir}"/
COPY --from=msr /opt/softwareag/IntegrationServer/lib/wm-isserver.jar "${__supplemental_cp_dir}"/

WORKDIR "${__workspace_home}"

VOLUME "${__workspace_home}" /tmp 
