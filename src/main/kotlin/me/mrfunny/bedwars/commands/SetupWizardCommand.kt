package me.mrfunny.bedwars.commands

import me.mrfunny.bedwars.game.GameManager
import me.mrfunny.bedwars.worlds.GameWorld
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class SetupWizardCommand(var gameManager: GameManager) : CommandExecutor {
    override fun onCommand(sender: CommandSender, p1: Command, p2: String, args: Array<out String>): Boolean {

        if(sender is Player){
            val player: Player = sender

            if(!player.hasPermission("bedwars.admin")) return true

            if(args.isEmpty()){
                player.sendMessage("/setup <map name>")
                return true
            }

            val mapName: String = args[0]
            if(mapName.equals("exit", true)){
                gameManager.setupWizardManager.removeFromWizard(player, gameManager)
                return true
            }
            player.sendMessage("Loading world, one moment...")
            val gameWorld = GameWorld(mapName, gameManager)
            gameWorld.loadWorld(gameManager, false) { gameManager.setupWizardManager.activateSetupWizard(player, gameWorld) }
        } else {
            sender.sendMessage("Only players can execute this command!")
        }

        return true
    }
}