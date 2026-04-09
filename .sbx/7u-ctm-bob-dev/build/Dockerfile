ARG __msr_image="ibmwebmethods.azurecr.io/webmethods-microservicesruntime:11.1.0.9"
ARG __alpine_java_bob_dev_image="iwcd-min-java-bob-dev-s:alpine"

FROM ${__msr_image} AS msr

# Build this using https://github.com/ibm-webmethods-continuous-delivery/7u-container-images/tree/main/images/s/alpine/min-java-bob-dev
FROM ${__alpine_java_bob_dev_image}

ARG __gid=1001
ARG __gname=7u-ctm-bob-dev
ARG __uid=1001
ARG __uname=7u-ctm-bob-dev

# this folder receives a mount of the main repository
ARG __workspace_home=/work

# this folder is expected to receive a mount for the user home
ARG __user_home=/home/${__uname}
ARG __supplemental_cp_dir=/cp
ARG __maven_repo_dir=${__user_home}/.m2
ARG __license_monitor_dir=${__workspace_home}/code/java/license-monitor

ENV 7U_CTM_USER_HOME=${__user_home} \
    7U_CTM_WORKSPACE=${__workspace_home} \
    MAVEN_CONFIG=${__maven_repo_dir}

RUN addgroup -g ${__gid} ${__gname} \
    && mkdir -p \
      "${__supplemental_cp_dir}" \
      "${__user_home}" \
      "${__workspace_home}" \
      "${__maven_repo_dir}" \
      /tmp \
    && adduser -u ${__uid} -G ${__gname} -D -h ${__user_home} ${__uname} \
    && chown -R ${__uid}:${__gid} \
      "${__supplemental_cp_dir}" \
      "${__user_home}" \
      "${__workspace_home}" \
      /tmp

COPY --from=msr /opt/softwareag/common/lib/wm-isclient.jar "${__supplemental_cp_dir}"/
COPY --from=msr /opt/softwareag/IntegrationServer/lib/wm-isserver.jar "${__supplemental_cp_dir}"/

USER ${__uname}

# Incorrect suggestion, project is known at run time, not at at docker build time
# WORKDIR "${__workspace_home}"/code/java/license-monitor
# RUN mvn --batch-mode dependency:go-offline

WORKDIR "${__workspace_home}"

VOLUME "${__workspace_home}" /tmp ${__user_home}
