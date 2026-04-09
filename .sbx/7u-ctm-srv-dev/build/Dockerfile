ARG __ibm_wm_msr_image=ibmwebmethods.azurecr.io/webmethods-microservicesruntime:11.1.0.9
FROM ${__ibm_wm_msr_image}

ARG __new_user_id=1001
ARG __new_grp_id=1001
ARG __img_user_name=sagadmin
ARG __img_grp_name=sagadmin

USER root

RUN usermod -u ${__new_user_id} ${__img_user_name} \
    && groupmod -g ${__new_grp_id} ${__img_grp_name} \
    && chown -R ${__new_user_id}:${__new_grp_id} /opt/softwareag

USER sagadmin
