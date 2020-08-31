# Fusiondirectory LSC Plugin

A Fusiondirectory connector for LSC (LDAP Synchronization Connector)

## Goal

This plugin synchronizes Fusiondirectory entities from/to another LSC compatible source/destination.

This plugin uses [FusionDirectory REST API](https://rest.fusiondirectory.org), available in version 4.

## Configuration

### PluginConnection

+ `name`: the name of the connection
+ `url`: the rest endpoint of Fusiondirectory
+ `username`: username of a user which has appropriate permissions in Fusiondirectory 
+ `password`: user password

Example: 

```
<pluginConnection>
        <name>fusiondirectory</name>
        <url>http://fusiondirectory.local/fusiondirectory/rest.php/v1</url>
        <username>fd-admin</username>
        <password>secret</password>
</pluginConnection>
```

### Service settings

+ `entity`: the type of entity to synchronize
+ `directory`: The LDAP directory to log into,  default is **"default"** (OPTIONAL)
+ `base`: An LDAP base to use (OPTIONAL)
+ `filter`: An LDAP filter to search with (OPTIONAL)
+ `pivot`: the pivot attribute name, default is **"uid"** (OPTIONAL)
+ `template`: The template to use to create objects (OPTIONAL)
+ `attributes`: Attribute names to fetch or update, grouped by tabs.

Example of source service :

```
<pluginSourceService implementationClass="org.lsc.plugins.connectors.fusiondirectory.FusionDirectorySrcService">
    <name>fusiondirectory-source-service</name>
    <connection reference="fusiondirectory" />
    <fusiondirectory:fusionDirectoryServiceSettings>
            <name>fusiondirectory-service-settings</name>
            <connection reference="fusiondirectory" />
            <fusiondirectory:entity>USER</fusiondirectory:entity>
            <fusiondirectory:pivot>uid</fusiondirectory:pivot>
            <fusiondirectory:attributes>
                    <fusiondirectory:tab name="user">
                            <string>uid</string>
                            <string>cn</string>
                            <string>sn</string>
                            <string>givenName</string>
                            <string>title</string>
                    </fusiondirectory:tab>
                    <fusiondirectory:tab name="mailAccount">
                            <string>mail</string>
                    </fusiondirectory:tab>
            </fusiondirectory:attributes>
    </fusiondirectory:fusionDirectoryServiceSettings>
</pluginSourceService>
```

Example of a destination service :

```
<pluginDestinationService implementationClass="org.lsc.plugins.connectors.fusiondirectory.FusionDirectoryDstService">
    <name>fusiondirectory-dest-service</name>
    <connection reference="fusiondirectory" />
    <fusiondirectory:entity>USER</fusiondirectory:entity>
    <fusiondirectory:pivot>uid</fusiondirectory:pivot>
    <fusiondirectory:attributes>
            <fusiondirectory:tab name="user">
                    <string>uid</string>
                    <string>cn</string>
                    <string>base</string>
                    <string>sn</string>
                    <string>givenName</string>
                    <string>title</string>
                    <string>userPassword</string>
            </fusiondirectory:tab>
            <fusiondirectory:tab name="mailAccount">
                    <string>mail</string>
            </fusiondirectory:tab>
    </fusiondirectory:attributes>
```

When using the destination service, the `mainIdentifier` holds the value of your pivot attribute, while the `base` attribute holds the location where the entity will be created or moved to.


## Examples

In the `etc` directory there are two configuration examples:

the directory `src-service` contains a configuration example of synchronization from Fusiondirectory to LDAP. 

The directory `dst-service`, contains a configuration example of synchronization from LDAP to Fusiondirectory

## Plugin loading

Install the lsc-fusiondirectory-plugin-%version%-distribution.jar in the lib/ directory of your LSC installation (`/usr/lib/lsc/`)

To load the plugin into LSC, you need to modify JAVA_OPTS: 

```
JAVA_OPTS="-DLSC.PLUGINS.PACKAGEPATH=org.lsc.plugins.connectors.fusiondirectory.generated"
```

For example, to run a user synchronization: 

```
JAVA_OPTS="-DLSC.PLUGINS.PACKAGEPATH=org.lsc.plugins.connectors.fusiondirectory.generated" lsc -f /etc/lsc/fusiondirectory/ -s user -t 1
```
