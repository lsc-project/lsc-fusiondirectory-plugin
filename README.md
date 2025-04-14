# Fusiondirectory LSC Plugin

A Fusiondirectory connector for LSC (LDAP Synchronization Connector)

## Goal

This plugin synchronizes Fusiondirectory entities from/to another LSC compatible source/destination.

This plugin uses [FusionDirectory REST API](https://rest-api.fusiondirectory.org/), available in version 1.4 of FusionDirectory

## Configuration

### XML namespace

You need to add the plugin namespace to the main lsc.xml file, for example:
```xml
<lsc xmlns="http://lsc-project.org/XSD/lsc-core-2.2.xsd" xmlns:fusiondirectory="http://lsc-project.org/XSD/lsc-fusiondirectory-plugin-1.0.xsd" revision="0">
```

### PluginConnection

+ `name`: the name of the connection
+ `url`: the rest endpoint of Fusiondirectory
+ `username`: username of a user which has appropriate permissions in Fusiondirectory 
+ `password`: user password

Example: 

```xml
<pluginConnection>
        <name>fusiondirectory</name>
        <url>http://fusiondirectory.local/fusiondirectory/rest.php/v1</url>
        <username>fd-admin</username>
        <password>secret</password>
</pluginConnection>
```

### Service settings

+ `entity`: the type of entity to synchronize (USER, OGROUP ...)
+ `directory`: The LDAP directory to log into,  default is **"default"** (OPTIONAL)
+ `base`: An LDAP base to use (OPTIONAL)
+ `filter`: An LDAP filter to search with (OPTIONAL)
+ `pivot`: the pivot attribute name, default is **"uid"** (OPTIONAL)
+ `template`: The template to use to create objects (OPTIONAL)
+ `attributes`: Attribute names to fetch or update, grouped by tabs.

Example of source service :

```xml
<pluginSourceService implementationClass="org.lsc.plugins.connectors.fusiondirectory.FusionDirectorySrcService">
    <name>fusiondirectory-source-service</name>
    <connection reference="fusiondirectory" />
    <fusiondirectory:serviceSettings>
        <name>fusiondirectory-service-settings</name>
        <connection reference="fusiondirectory" />
        <fusiondirectory:entity>USER</fusiondirectory:entity>
        <fusiondirectory:pivot>uid</fusiondirectory:pivot>
        <fusiondirectory:attributes>
            <fusiondirectory:tab name="user">
                    <fusiondirectory:attribute>uid</fusiondirectory:attribute>
                    <fusiondirectory:attribute>cn</fusiondirectory:attribute>
                    <fusiondirectory:attribute>base</fusiondirectory:attribute>
                    <fusiondirectory:attribute>sn</fusiondirectory:attribute>
                    <fusiondirectory:attribute>givenName</fusiondirectory:attribute>
                    <fusiondirectory:attribute multiple="true">title</fusiondirectory:attribute>
                    <fusiondirectory:attribute>userPassword</fusiondirectory:attribute>
            </fusiondirectory:tab>
            <fusiondirectory:tab name="mailAccount">
                    <fusiondirectory:attribute>mail</fusiondirectory:attribute>
            </fusiondirectory:tab>
        </fusiondirectory:attributes>
    </fusiondirectory:serviceSettings>
</pluginSourceService>
```

Example of a destination service :

```xml
<pluginDestinationService implementationClass="org.lsc.plugins.connectors.fusiondirectory.FusionDirectoryDstService">
    <name>fusiondirectory-dest-service</name>
    <connection reference="fusiondirectory" />
    <fusiondirectory:serviceSettings>
        <name>fusiondirectory-service-settings</name>
        <connection reference="fusiondirectory" />
        <fusiondirectory:entity>USER</fusiondirectory:entity>
        <fusiondirectory:pivot>uid</fusiondirectory:pivot>
        <fusiondirectory:attributes>
            <fusiondirectory:tab name="user">
                <fusiondirectory:attribute>uid</fusiondirectory:attribute>
                <fusiondirectory:attribute>cn</fusiondirectory:attribute>
                <fusiondirectory:attribute>base</fusiondirectory:attribute>
                <fusiondirectory:attribute>sn</fusiondirectory:attribute>
                <fusiondirectory:attribute>givenName</fusiondirectory:attribute>
                <fusiondirectory:attribute multiple="true">title</fusiondirectory:attribute>
                <fusiondirectory:attribute passwordHash="clear">userPassword</fusiondirectory:attribute>
            </fusiondirectory:tab>
            <fusiondirectory:tab name="mailAccount">
                <fusiondirectory:attribute>mail</fusiondirectory:attribute>
            </fusiondirectory:tab>
        </fusiondirectory:attributes>
    </fusiondirectory:serviceSettings>
</pluginDestinationService>
```

Multiple attributes in Fusiondirectory must be declared as so:

```xml
<fusiondirectory:attribute multiple="true">title</fusiondirectory:attribute>
```

Attribute user/userPassword may define a passwordHash with a value chosen from Fusiondirectory > Configuration > Password settings > allowed password hashes:

```xml
<fusiondirectory:attribute passwordHash="clear">userPassword</fusiondirectory:attribute>
```

When attribute `passwordHash` is set, the password will be sent as an array `[%passwordHash%,%newpassword%,%newpassword%,"",""]` to instruct FusionDirectory how to hash password before storing it into OpenLDAP.

Tips:

* To allow Fusiondirectory to create a new user without any password, configure "empty" in Fusiondirectory Configuration > Password settings > Password default hash.
* `passwordHash="clear"` can be used to let OpenLDAP apply its own hashing rules.

#### Filters

You *may* defined these filters to retrieve objects:

* _allFilter_: filter to retrieve all entries during sync phase or clean phase.
* _oneFilter_: computed filter to retrieve one entry in destination during sync phase (you can use pivot name as placeholder eg. `(uid={cn})`) like in a regular LSC connector. 
* _cleanFilter_: computed filter to retrieve one entry in source during clean phase (you can use pivot name as placeholder eg. `(uid={cn})`) like in a regular LSC connector.
* _filter_: Default filter to retrieve all or individual entries during sync phase or clean phase.

If no filters are defined, the connector will retrieve all entries of type _entity_ in _base_ branch and retrieve individual entries based on its _pivot_ attribute matching the pivot value(s) of the source entry.

### propertiesBasedSyncOptions

When using the destination service, the `mainIdentifier` holds the value of your pivot attribute:

```xml
<mainIdentifier><![CDATA[ srcBean.getDatasetFirstValueById("uid") ]]></mainIdentifier>
```

The `base` attribute holds the location where the entity will be created or moved to:

```xml
<dataset>
    <name>base</name>
    <policy>FORCE</policy>
    <forceValues>
        <string><![CDATA["ou=hr,dc=company,dc=com"]]></string>
    </forceValues>
</dataset>
```

## Scripting

` org.lsc.plugins.connectors.fusiondirectory.utils.FusionDirectoryAPI` gives access to Fusiondirectory API and is useful for scripting.

First, declare it as a customLibrary:

```xml
<task>
...
	<customLibrary>
		<string>org.lsc.plugins.connectors.fusiondirectory.utils.FusionDirectoryAPI</string>
	</customLibrary>
</task>
```

Then, an instance of the class can be accessed in scripts using `custom[0]`:

```xml
<dataset>
  <name>member</name>
  <forceValues>
    <string><![CDATA[
    var fdAPI = custom[0];
    ...
     ]]></string>
  </forceValues>
</dataset>
```

This library has available methods:

* Log in FusionDirectory if no session has been previously opened: void connect(_endpoint_, _username_, _password_);
* Search entities DN within base using ldap filter: List<String> search(_entity_, _base_, _filter_);
* Get attribute values for an entity with this DN: List<String> getAttributeValues(_entity_, _dn_, _attribute_);
* Update entity single attribute value: void setAttribute(_entity_, _dn_, _tab_, _attribute_, _value_);
* Update entity multiple attribute value: void setAttribute(_entity_, _dn_, _tab_, _attribute_, _values_);

Here is a script example of resolving group members DN from AD to FusionDirectory:

```xml
<dataset>
  <name>member</name>
  <forceValues>
    <string><![CDATA[
      var fdAPI = custom[0];
      fdAPI.connect('https://fd.example.com/rest.php/v1','fd-admin','secret');
      var srcMembers = srcBean.getDatasetValuesById("member");
      var dstMembers = [];
      for (var i=0; i<srcMembers.size(); i++) {
        var srcMemberDn = srcMembers[i];
        var sAMAccountName = "";
        try {
          sAMAccountName = srcLdap.attribute(srcMemberDn, "sAMAccountName").get(0);
        } catch (e) {
          continue;
        }
        var dstMember = fdAPI.search("USER","dc=example,dc=com","(uid=" + sAMAccountName + ")");
        if (dstMember.size() != 1) { continue; }
        dstMembers.push(dstMember.get(0));
      }
      dstMembers
    ]]></string>
  </forceValues>
</dataset>
```

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
