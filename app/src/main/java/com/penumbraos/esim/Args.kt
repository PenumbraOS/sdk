@file:OptIn(ExperimentalCli::class)

package com.penumbraos.esim

import android.util.Log
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand

class Args {
    companion object {
        fun parse(args: Array<String>): Pair<String, Array<String>> {
            val parser = ArgParser("esim")

            var command: String? = null
            var params = mutableListOf<String>()

            class DownloadProfile : Subcommand("download-profile", "Download a profile") {
                val activationCode by argument(ArgType.String, description = "Activation code for the profile")
                override fun execute() {
                    command = "downloadProfile"
                    params = mutableListOf(activationCode)
                }
            }

            class DownloadAndEnableProfile : Subcommand("download-and-enable-profile", "Download and enable a profile") {
                val activationCode by argument(ArgType.String, description = "Activation code for the profile")
                override fun execute() {
                    command = "downloadAndEnableProfile"
                    params = mutableListOf(activationCode)
                }
            }

            class DownloadVerifyAndEnableProfile : Subcommand("download-verify-and-enable-profile", "Download, verify and enable a profile") {
                val activationCode by argument(ArgType.String, description = "Activation code for the profile")
                override fun execute() {
                    command = "downloadVerifyAndEnableProfile"
                    params = mutableListOf(activationCode)
                }
            }

            class GetProfiles : Subcommand("get-profiles", "List all profiles") {
                override fun execute() {
                    command = "getProfiles"
                }
            }

            class GetActiveProfile : Subcommand("get-active-profile", "Get active profile details") {
                override fun execute() {
                    command = "getActiveProfile"
                }
            }

            class GetActiveProfileIccid : Subcommand("get-active-profile-iccid", "Get active profile ICCID") {
                override fun execute() {
                    command = "getActiveProfileIccid"
                }
            }

            class EnableProfile : Subcommand("enable-profile", "Enable a profile") {
                val iccid by argument(ArgType.String, description = "ICCID of the profile to enable")
                override fun execute() {
                    command = "enableProfile"
                    params = mutableListOf(iccid)
                }
            }

            class DisableProfile : Subcommand("disable-profile", "Disable a profile") {
                val iccid by argument(ArgType.String, description = "ICCID of the profile to disable")
                override fun execute() {
                    command = "disableProfile"
                    params = mutableListOf(iccid)
                }
            }

            class DisableActiveProfile : Subcommand("disable-active-profile", "Disable the active profile") {
                override fun execute() {
                    command = "disableActiveProfile"
                }
            }

            class DeleteProfile : Subcommand("delete-profile", "Delete a profile") {
                val iccid by argument(ArgType.String, description = "ICCID of the profile to delete")
                override fun execute() {
                    command = "deleteProfile"
                    params = mutableListOf(iccid)
                }
            }

            class SetNickname : Subcommand("set-nickname", "Set profile nickname") {
                val iccid by argument(ArgType.String, description = "ICCID of the profile")
                val nickname by argument(ArgType.String, description = "New nickname for the profile")
                override fun execute() {
                    command = "setNickname"
                    params = mutableListOf(iccid, nickname)
                }
            }

            class GetEid : Subcommand("get-eid", "Get eUICC identifier") {
                override fun execute() {
                    command = "getEid"
                }
            }

            class GetEuiccInfo2 : Subcommand("get-euicc-info2", "Get eUICC information") {
                override fun execute() {
                    command = "getEuiccInfo2"
                }
            }

//            class SetDefDpplusAddr : Subcommand("set-def-dpplus-addr", "Set default DP+ address") {
//                val address by argument(ArgType.String, description = "Default DP+ address to set")
//                override fun execute() {
//                    command = "setDefDpplusAddr"
//                }
//            }

            class GetDefDpplusAddr : Subcommand("get-def-dpplus-addr", "Get default DP+ address") {
                override fun execute() {
                    command = "getDefDpplusAddr"
                }
            }

//            class ResetMemory : Subcommand("reset-memory", "Reset memory") {
//                val delOpProf by option(ArgType.Boolean, shortName = "o", description = "Delete operational profiles").default(false)
//                val delFieldLoadedTestProf by option(ArgType.Boolean, shortName = "t", description = "Delete field loaded test profiles").default(false)
//                val resetDefDpplus by option(ArgType.Boolean, shortName = "d", description = "Reset default DP+ address").default(false)
//                override fun execute() {
//                    command = "resetMemory"
//                    params = mutableListOf(delOpProf.toString(), delFieldLoadedTestProf.toString(), resetDefDpplus.toString())
//                }
//            }

//            class ResetTestMemory : Subcommand("reset-test-memory", "Reset test memory") {
//                override fun execute() {
//                    command = "resetTestMemory"
//                }
//            }

            // Register all subcommands
            parser.subcommands(
                DownloadProfile(),
                DownloadAndEnableProfile(),
                DownloadVerifyAndEnableProfile(),
                GetProfiles(),
                GetActiveProfile(),
                GetActiveProfileIccid(),
                EnableProfile(),
                DisableProfile(),
                DisableActiveProfile(),
                DeleteProfile(),
                SetNickname(),
                GetEid(),
                GetEuiccInfo2(),
//                SetDefDpplusAddr(),
                GetDefDpplusAddr(),
//                ResetMemory(),
//                ResetTestMemory()
            )

            try {
                if (args.isEmpty()) {
                    parser.parse(arrayOf("--help"))
                } else {
                    parser.parse(args)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing arguments: ${e.message}")
                println("Error: ${e.message}")
                println("Use --help for usage information")
            }

            if (command == null) {
                Log.e(TAG, "Error parsing arguments")
                println("Use --help for usage information")
                throw Exception("No command specified")
            }

            return Pair(command, params.toTypedArray())
        }
    }
}