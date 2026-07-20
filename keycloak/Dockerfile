FROM quay.io/keycloak/keycloak:24.0

COPY realm-export.json /opt/keycloak/data/import/realm-export.json

EXPOSE 8080

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start-dev", "--import-realm", "--hostname-strict=false", "--proxy-headers=xforwarded"]
