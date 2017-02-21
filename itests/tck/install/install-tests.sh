mvn org.apache.maven.plugins:maven-install-plugin:install-file -Dfile=$1/org.osgi.test.cases.remoteserviceadmin-5.0.0.jar \
  -DgroupId=org.osgi.test.cases \
  -DartifactId=org.osgi.test.cases.remoteserviceadmin \
  -Dversion=5.0.0 \
  -Dpackaging=jar \
  -DgeneratePom=true
