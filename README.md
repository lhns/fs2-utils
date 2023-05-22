# fs2-utils

[![Test Workflow](https://github.com/lhns/fs2-utils/workflows/test/badge.svg)](https://github.com/lhns/fs2-utils/actions?query=workflow%3Atest)
[![Release Notes](https://img.shields.io/github/release/lhns/fs2-utils.svg?maxAge=3600)](https://github.com/lhns/fs2-utils/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/de.lhns/fs2-utils_2.13)](https://search.maven.org/artifact/de.lhns/fs2-utils_2.13)
[![Apache License 2.0](https://img.shields.io/github/license/lhns/fs2-utils.svg?maxAge=3600)](https://www.apache.org/licenses/LICENSE-2.0)

This project provides several utility methods for [fs2](https://github.com/typelevel/fs2).

## Usage

### build.sbt

```sbt
// use this snippet for fs2 3 and the JVM
libraryDependencies += "de.lhns" %% "fs2-utils" % "0.4.0"

libraryDependencies += "de.lhns" %% "fs2-io-utils" % "0.4.0"

// use this snippet for fs2 3 and JS, or cross-building
libraryDependencies += "de.lhns" %%% "fs2-utils" % "0.4.0"
```

## License

This project uses the Apache 2.0 License. See the file called LICENSE.
