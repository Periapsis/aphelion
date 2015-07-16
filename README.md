# Aphelion
2D spaceship mmog written in java with an authoritative server model. Inspired by subspace.

## Goals

1. An open source game anyone can contribute to while still preventing most cheats, especially the severe ones.
Players should be able to play the game with modified clients.

2. Anyone can run their own server (zone) provided their connection is good enough. Or by hosting their game in a subarena on someone elses server.

3. Modding should be easy, fun, collaborative and for a large part real-time.

4. Avoid politics by making sure shared services such as nickname registration does not involve itself with how players act in the game itself.

## Building
You can build this project using maven ("mvn package"). However there are a few steps you need to take care of first:

1. The command "protoc" from <a href="https://code.google.com/p/protobuf/">google protobuf</a> needs to be on your PATH. If you are on windows, "protoc.exe" has been included and should be picked up by maven.

2. (Optional) If you would like to redistribute this project, you can place an OpenJDK (7+) distribution (binary, not source) in the proper folders within target/openjdk. Maven will then generate uncompressed zip files in the target directory with OpenJDK included, this lets a user play the game without having java installed.

## Subspace
The first versions of this game will be based around the existing game subspace. However aphelion will not be a drop-in replacement, the architecture of that game conflicts with our goals. Most or all of the gameplay of subspace will be supported, but will be implemented in different ways.

## License
This project is released under the GNU Affero General Public License version 3 with classpath exception. The classpath exception gives you permission to link this library/application with independent modules to produce an executable, regardless of the license terms of these independent modules (see LICENSE.txt for more details). Without this exception the AGPL license would force you to use a GPL compatible license for any module that is merely linked with or loaded by aphelion.
