# Why are these libraries here?

Freerouting uses the Java module system. This requires all of the referenced libraries to have module names defined, but some of them don't have that by default. In order to make them available in Freerouting, module names must be included in their .jar file. This can be done by inserting a line into their `MANIFEST.MF` file in their `META-INF` folder.

For example the original `org.eclipse.jetty.servlet` library has the following definitions in its `MANIFEST.MF`:

```
Manifest-Version: 1.0
Created-By: Apache Maven Bundle Plugin 5.1.9
...
```

And we need to modify the contents in the following way:

```
Manifest-Version: 1.0
Automatic-Module-Name: org.eclipse.jetty.servlet
Created-By: Apache Maven Bundle Plugin 5.1.9
...
```

After it's repackaged the file is renamed to its `-mod` variant, and that is referenced in the [build.gradle](https://github.com/freerouting/freerouting/blob/b49b84b75f30ab3bd0e1600e4820a38d2681c57e/build.gradle#L70) file of Freerouting.

If the modification was successful, we can add the appropriate requires line into our [module-info.java](https://github.com/freerouting/freerouting/blob/b49b84b75f30ab3bd0e1600e4820a38d2681c57e/src/main/java/module-info.java#L25). This step make the library classes available for our codebase.

Keeping all these libraries up-to-date is an extra chore and cause version conflicts, but it would be still desirable.
