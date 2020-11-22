package me.mrfunny.plugins.paper.events

import me.mrfunny.plugins.paper.gui.SetupWizardIslandSelectorGUI
import me.mrfunny.plugins.paper.gamemanager.GameManager
import me.mrfunny.plugins.paper.gamemanager.GameState
import me.mrfunny.plugins.paper.gui.ItemShopGUI
import me.mrfunny.plugins.paper.gui.TeamPickerGUI
import me.mrfunny.plugins.paper.worlds.Island
import me.mrfunny.plugins.paper.worlds.generators.Generator
import me.mrfunny.plugins.paper.worlds.generators.GeneratorType
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Fireball
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent

class PlayerItemInteractListener(var gameManager: GameManager) : Listener {

    @EventHandler
    fun onInteractWithShop(event: PlayerInteractEntityEvent){
        val name: String = ChatColor.stripColor(event.rightClicked.name.toLowerCase())

        if(name == "магазин предметов") {
            event.isCancelled = true
            val gui = ItemShopGUI(gameManager, event.player)
            gameManager.guiManager.setGUI(event.player, gui)
        } else if(name == "улучшения команды"){

        }

    }

    @ExperimentalStdlibApi
    @EventHandler
    fun onInteract(event: PlayerInteractEvent){
        if(!event.hasItem()) return
        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            if(gameManager.state != GameState.ACTIVE) return
            if (event.item!!.type == Material.LEGACY_FIREBALL || event.item!!.type == Material.FIRE_CHARGE) {
                event.isCancelled = true
                val newItem = event.item!!
                newItem.amount = newItem.amount - 1
                event.player.inventory.remove(event.item!!)
                event.player.inventory.addItem(newItem)
                event.player.updateInventory()
                val fireball = event.player.launchProjectile(Fireball::class.java)
                fireball.direction = event.player.location.direction
                fireball.location.y = fireball.location.y - 0.5
            }
        }

        if(event.item == null) return
        if(event.item!!.itemMeta == null) return
        if(!(event.item!!.hasItemMeta())) return

        val player: Player = event.player

        val itemName: String? = ChatColor.stripColor(event.item?.itemMeta?.displayName).toLowerCase()

        if(itemName == "select team" && gameManager.state == GameState.LOBBY || gameManager.state == GameState.STARTING){
            val teamPickerGUI = TeamPickerGUI(gameManager, event.player)
            gameManager.guiManager.setGUI(player, teamPickerGUI)
            event.isCancelled = true
            return
        }

        val current: Location = player.location
        val clicked: Location = if(event.clickedBlock != null) event.clickedBlock!!.location else player.location
        val island: Island? = gameManager.setupWizardManager.getIsland(player)

        if(itemName == null){
            return
        }

        event.isCancelled = true

        if(!gameManager.setupWizardManager.isInWizard(event.player)) return

        when(itemName){
            "set diamond generator" -> {
                val diamondGenerator = Generator(current, GeneratorType.DIAMOND, false)
                gameManager.configurationManager.saveUnownedGenerator(player.world.name, diamondGenerator)
            }
            "set emerald generator" -> {
                val emeraldGenerator = Generator(current, GeneratorType.EMERALD, false)
                gameManager.configurationManager.saveUnownedGenerator(player.world.name, emeraldGenerator)
            }
            "change island" -> {
                val gui = SetupWizardIslandSelectorGUI(gameManager)
                gameManager.guiManager.setGUI(player, gui)
            }
            "first corner stick" -> {
                if(island != null){
                    player.sendMessage("setting first corner")
                    island.protectedCorner1 = clicked
                }
            }
            "second corner stick" -> {
                if(island != null) {
                    player.sendMessage("setting second corner")
                    island.protectedCorner2 = clicked
                }
            }
            "set shop location" -> {
                if(island != null) {
                    player.sendMessage("setting shop location")
                    island.shopEntityLocation = current
                }
            }
            "set generator location" -> {
                if(island != null) {
                    player.sendMessage("setting generator location")

                    var islandGenerator = Generator(current, GeneratorType.IRON, true)
                    island.islandGenerators.add(islandGenerator)

                    islandGenerator = Generator(current, GeneratorType.GOLD, true)
                    island.islandGenerators.add(islandGenerator)
                    islandGenerator = Generator(current, GeneratorType.EMERALD, true)
                    island.islandGenerators.add(islandGenerator)
                }
            }
            "set team upgrade location" -> {
                if(island != null) {
                    player.sendMessage("setting team upgrade location")
                    island.upgradeEntityLocation = current
                }
            }
            "set spawn location" -> {
                if(island != null){
                    island.spawnLocation = current
                }
            }
            "set bed location" -> {
                if(island != null) {
                    island.bedLocation = clicked
                    player.sendMessage("setting bed location")
                }
            }
            "set lobby spawn" -> {
                player.sendMessage("Setting lobby spawn")
                gameManager.setupWizardManager.getWorld(player)!!.lobbyPosition = current
                gameManager.configurationManager.saveWorld(gameManager.setupWizardManager.getWorld(player)!!)
            }
            "save island" -> {
                println("saving")
                if(island != null){
                    gameManager.configurationManager.saveIsland(island)
                    Bukkit.getServer().scheduler.runTaskLater(gameManager.plugin, {task ->
                            gameManager.setupWizardManager.worldSetupWizard(player, island.gameWorld)
                    }, 4)
                }
            }
            else -> return
        }

    }
}
