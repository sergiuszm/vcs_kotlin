package vcs

import java.io.File
import java.security.MessageDigest

const val CMD_LENGTH = 10
const val HELP_ARG = "--help"

const val ARGS_NR = 2
const val VCS_DIR = "vcs"
const val VCS_COMMITS_DIR = "commits"
const val VCS_CONFIG = "config.txt"
const val VCS_INDEX = "index.txt"
const val VCS_LOG = "log.txt"

val vcsDir = File(VCS_DIR)
val vcsCommitsDir = File("$VCS_DIR${File.separator}$VCS_COMMITS_DIR")
val vcsConfig = File("$VCS_DIR${File.separator}$VCS_CONFIG")
val vcsIndex = File("$VCS_DIR${File.separator}$VCS_INDEX")
val vcsLog = File("$VCS_DIR${File.separator}$VCS_LOG")
val absPath = File(System.getProperty("user.dir"))


enum class COMMANDS(val cmd: String) {
    CONFIG("config"),
    ADD("add"),
    LOG("log"),
    COMMIT("commit"),
    CHECKOUT("checkout"),
}

val commandsHelp = mapOf(
    COMMANDS.CONFIG.cmd to "Get and set a username.",
    COMMANDS.ADD.cmd to "Add a file to the index.",
    COMMANDS.LOG.cmd to "Show commit logs.",
    COMMANDS.COMMIT.cmd to "Save changes.",
    COMMANDS.CHECKOUT.cmd to "Restore a file."
)

val commandsExpectedInput = mapOf(
    COMMANDS.CONFIG.cmd to "Please, tell me who you are.",
    COMMANDS.ADD.cmd to "Add a file to the index.",
    COMMANDS.LOG.cmd to "Show commit logs.",
    COMMANDS.COMMIT.cmd to "Message was not passed.",
    COMMANDS.CHECKOUT.cmd to "Commit id was not passed."
)

fun main(args: Array<String>) {
    setup()
    when {
        args.isEmpty() || (args.first() == HELP_ARG) -> printHelp()
        args.first() in commandsHelp -> handleCommand(args)
        else -> println("'${args.first()}' is not a SVCS command.")
    }
}

fun setup() {
    if (!vcsDir.exists()) vcsDir.mkdir()
    if (!vcsCommitsDir.exists()) vcsCommitsDir.mkdir()
    if (!vcsConfig.exists()) vcsConfig.createNewFile()
    if (!vcsIndex.exists()) vcsIndex.createNewFile()
    if (!vcsLog.exists()) vcsLog.createNewFile()
}

fun printHelp() {
    println("These are SVCS commands:")
    commandsHelp.forEach { (cmd, dsc) -> println("%-${CMD_LENGTH}s %s".format(cmd, dsc)) }
}

fun handleCommand(args: Array<String>) {
    when (args.first()) {
        COMMANDS.CONFIG.cmd -> commandConfig(args)
        COMMANDS.ADD.cmd -> commandAdd(args)
        COMMANDS.COMMIT.cmd -> commandCommit(args)
        COMMANDS.LOG.cmd -> commandLog()
        COMMANDS.CHECKOUT.cmd -> commandCheckout(args)
        else -> println(commandsHelp[args.first()])
    }
}

fun commandConfig(args: Array<String>) {
    val username = vcsConfig.readText()
    when {
        args.size == 1 && username.isNotEmpty() -> println("The username is $username.")
        args.size == 1 -> println(commandsExpectedInput[COMMANDS.CONFIG.cmd])
        args.size == 2 -> {
            vcsConfig.writeText(args.last())
            println("The username is ${args.last()}.")
        }
        else -> println("Unsupported operation!")
    }
}

fun commandAdd(args: Array<String>) {
    val trackedFiles = vcsIndex.readLines().toMutableList()
    when {
        args.size == 1 && trackedFiles.size > 0 -> {
            println("Tracked files:")
            trackedFiles.forEach { println(it) }
        }
        args.size == 1 -> println(commandsExpectedInput[COMMANDS.ADD.cmd])
        args.size == 2 && absPath.listFiles()?.any { it.name == args.last() } ?: false -> {
            println("The file '${args.last()}' is tracked.")
            if (args.last() !in trackedFiles) {
                trackedFiles.add(args.last())
                vcsIndex.writeText(trackedFiles.joinToString("\n"))
            }
        }
        args.size == 2 -> println("Can't find '${args.last()}'.")
        else -> println("Unsupported operation!")
    }
}

fun commandCommit(args: Array<String>) {
    val author = vcsConfig.readText()
    val filesToCommit = vcsIndex.readLines()
    val hash = if (filesToCommit.isNotEmpty()) computeHash() else ""
    val commitDir = if (hash.isNotEmpty()) File("${vcsCommitsDir.path}${File.separator}$hash") else null
    val hasChanged = when {
        hash.isNotEmpty() && commitDir is File && commitDir.exists() -> false
        else -> true
    }

    when {
        args.size == 1 -> println(commandsExpectedInput[COMMANDS.COMMIT.cmd])
        args.size == 2 && hasChanged && commitDir is File -> {
            commitDir.mkdir()
            filesToCommit.forEach {
                val (from, to) = Pair("${absPath.path}${File.separator}$it", "${commitDir.path}${File.separator}$it")
                File(from).copyTo(File(to))
            }
//            vcsIndex.writeText("")
            val log = vcsLog.readLines().joinToString("\n")
            val newLog = mutableListOf<String>("commit $hash", "Author: $author", args.last(), "\n").joinToString("\n")
            vcsLog.writeText(newLog + log)
            println("Changes are committed.")
        }
        args.size == 2 -> println("Nothing to commit.")
        else -> println("Unsupported operation!")
    }
}

fun commandCheckout(args: Array<String>) {
    val doesCommitExists = vcsCommitsDir.list()?.any{ it == args.last() }

    when {
        args.size == 1 -> println(commandsExpectedInput[COMMANDS.CHECKOUT.cmd])
        args.size == 2 && doesCommitExists == true -> {
            val commitDir = File("${vcsCommitsDir.path}${File.separator}${args.last()}")
            commitDir.listFiles()?.forEach {
                val (to, from) = Pair("${absPath.path}${File.separator}${it.name}", it.toString())
                File(from).copyTo(File(to), overwrite = true)
            }
            println("Switched to commit ${args.last()}.")
        }
        args.size == 2 -> println("Commit does not exist.")
    }
}

fun commandLog() {
    val log = vcsLog.readLines()
    if (log.isEmpty()) println("No commits yet.")
    else log.forEach { println(it) }
}

fun computeHash(): String {
    val lengthThenNatural = compareBy<String> { it.length }.then(naturalOrder())
    var input = ""
    vcsIndex.readLines().sortedWith(lengthThenNatural).forEach {
        input += it
        input += File("${absPath.path}${File.separator}$it").readLines().joinToString()
    }

    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())

    return bytes.joinToString("") { "%02x".format(it) }
}