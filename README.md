# Nachos
The following project is an implementation of a operating system based on NachOs for Java 1.5.

## Installation Instructions
1. Install [NachOs](https://catcourses.ucmerced.edu/courses/9717/files/folder/Projects/Project%201?preview=1255291)
2. Replace the `nachos` folder with this respository instead (below is how to do it in linux/mac terminal)
```$xslt
cd path/to/nachos
cd ..                      # goes up by one folder
rm -rf nachos
git clone https://github.com/NachoSquad/nachos.git nachos
```

## Compiling Software
1. Navigate to the current project folder (such as `proj1`)
1. Type `make` in your terminal

## Using git
Once you have made a substantial update to your codebase, do the following to update the repo:
```
git add .                           # adds any untracked files to github
git commit -am 'your-message-here'  # commits your changes (please add a useful change message)
git push origin master              # pushes your commits to the server
```

For more information about git, [click here](https://try.github.io/levels/1/challenges/1)

## FAQ
**Q: Why does the `nachos` file in `proj1` not show up on the github?**\
That folder is not needed to write code with and is only used by your compiler, it is ignored in our `.gitignore` file at the root of the directory!
