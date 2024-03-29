package me.mrfunny.bedwars.events

import com.google.common.io.ByteArrayDataOutput
import com.google.common.io.ByteStreams
import me.mrfunny.bedwars.BedWars.Companion.colorize
import me.mrfunny.bedwars.game.GameManager
import me.mrfunny.bedwars.game.GameState
import me.mrfunny.bedwars.gui.*
import me.mrfunny.bedwars.players.NoFallPlayers
import me.mrfunny.bedwars.players.PlayerData
import me.mrfunny.bedwars.tasks.PlatformWaitTask
import me.mrfunny.bedwars.tasks.TeleportTask
import me.mrfunny.bedwars.worlds.generators.Generator
import me.mrfunny.bedwars.worlds.generators.GeneratorType
import me.mrfunny.bedwars.worlds.islands.Island
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.entity.EntityType
import org.bukkit.entity.Fireball
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import kotlin.math.abs
import kotlin.math.floor

class PlayerItemInteractListener(var gameManager: GameManager) : Listener {

    @EventHandler
    fun onInteractWithShop(event: PlayerInteractEntityEvent){
        if(event.player.gameMode == GameMode.SPECTATOR){
            event.isCancelled = true
            return
        }
        val name: String = ChatColor.stripColor(event.rightClicked.name.toLowerCase())

        if(name == "item shop") {
            event.isCancelled = true
            val gui = ItemShopGUI(gameManager, event.player)
            gameManager.guiManager.setGUI(event.player, gui)
        } else if(name == "team upgrades"){
            event.isCancelled = true
            val gui = TeamUpgradeGUI(gameManager)
            gameManager.guiManager.setGUI(event.player, gui)
        }

    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent){
        if(event.clickedBlock != null){
            val type = event.clickedBlock!!.type
            if(type == Material.CRAFTING_TABLE || type == Material.ENDER_CHEST){
                event.isCancelled = true
                return
            }
            if(event.clickedBlock!!.state is Container && gameManager.state != GameState.ACTIVE){
                event.isCancelled = true
                return
            }
        }
        if(event.action == Action.LEFT_CLICK_BLOCK  && event.item != null){
            if(event.item!!.type == Material.BARRIER){
                event.isCancelled = true
                return
            }
        }
        if(gameManager.state != GameState.ACTIVE){
            event.isCancelled = true
        }
        if(!event.hasItem()) return

        if(event.item == null) return
        if(event.item!!.itemMeta == null) return

        val player: Player = event.player

        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            if (event.item!!.type == Material.FIRE_CHARGE) {
                event.isCancelled = true
                if(gameManager.state == GameState.ACTIVE){
                    val newItem = event.item!!
                    newItem.amount = newItem.amount - 1
                    event.player.inventory.remove(event.item!!)
                    event.player.inventory.addItem(newItem)
                    event.player.updateInventory()
                    val fireball = event.player.launchProjectile(Fireball::class.java)
                    fireball.yield = 2f
                    fireball.direction = event.player.location.direction
                    fireball.location.y = fireball.location.y - 0.5
                    fireball.velocity.multiply(2)
                    return
                }
            } else if(event.item!!.type == Material.RED_BED){
                val out: ByteArrayDataOutput = ByteStreams.newDataOutput()
                out.writeUTF("Connect")
                out.writeUTF("hub")
                player.sendPluginMessage(gameManager.plugin, "BungeeCord", out.toByteArray())
            }
        }

        // todo: bonuses

        if(event.action == Action.RIGHT_CLICK_BLOCK){
            if(event.clickedBlock!!.type.name.contains("BED") && !player.isSneaking){
                event.isCancelled = true
            }
        }

        val itemName: String = ChatColor.stripColor(event.item?.itemMeta?.displayName).toLowerCase()
        val current: Location = player.location
        val clicked: Location = if(event.clickedBlock != null) event.clickedBlock!!.location else player.location
        if((itemName == "select team" || itemName == "выбрать команду") && (gameManager.state == GameState.LOBBY || gameManager.state == GameState.STARTING)){
            val teamPickerGUI = TeamPickerGUI(gameManager, event.player)
            gameManager.guiManager.setGUI(player, teamPickerGUI)
            event.isCancelled = true
            return
        }

        val item: ItemStack = event.item!!

        if(item.type == Material.BLAZE_POWDER){
            gameManager.guiManager.setGUI(player, StartPowerGUI(gameManager, player))
            event.isCancelled = true
            return
        }

        if(gameManager.state == GameState.ACTIVE && (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)){
            val currentIsland: Island = gameManager.world.getIslandForPlayer(player)!!

            if(item.type == Material.TNT){
                if(event.action == Action.RIGHT_CLICK_AIR){
                    event.isCancelled = true
                    val location = player.location.direction
                    location.x += 0.5
                    location.y += 0.5
                    location.z += 0.5
                    val tnt: TNTPrimed = (player.location.world!!.spawnEntity(player.location.clone().add(player.location.direction.normalize().multiply(0.2)), EntityType.PRIMED_TNT) as TNTPrimed)
                    tnt.fuseTicks = 60
                    event.item!!.amount--
                    event.player.updateInventory()
                    tnt.velocity = player.location.direction.normalize().multiply(1.5)
                    return
                }
            }

            if(item.type == Material.BLAZE_ROD){
                event.isCancelled = true
                val data = PlayerData.get(player)
                if((System.currentTimeMillis() - data.lastPlatformUse) < 15000){
                    player.sendTitle("${ChatColor.RED}Cooldown ${floor((15L - (System.currentTimeMillis() - data.lastPlatformUse) / 1000.0))}", "", 5, 40, 10)
                    return
                }
                item.amount--
                event.player.updateInventory()
                NoFallPlayers.add(player)
                data.lastPlatformUse = System.currentTimeMillis()
                val playerLocation: Location = player.location

                val teleportLoc: Location = playerLocation.clone().add(0.0, if(playerLocation.y < 3) abs(player.location.y) + 2.0 else 1.0, 0.0)
                player.teleport(teleportLoc)

                val locations = arrayOf(
                    teleportLoc.clone().add(0.0, -1.0, 1.0),
                    teleportLoc.clone().add(-1.0, -1.0, 2.0),
                    teleportLoc.clone().add(1.0, -1.0, 2.0),
                    teleportLoc.clone().add(1.0, -1.0, 1.0),
                    teleportLoc.clone().add(-1.0, -1.0, 1.0),

                    teleportLoc.clone().add(1.0, -1.0, 0.0),
                    teleportLoc.clone().add(-1.0, -1.0, 0.0),

                    teleportLoc.clone().add(.0, -1.0, .0),
                    teleportLoc.clone().add(-2.0, -1.0, 1.0),
                    teleportLoc.clone().add(-2.0, -1.0, -1.0),
                    teleportLoc.clone().add(2.0, -1.0, 1.0),
                    teleportLoc.clone().add(2.0, -1.0, -1.0),

                    teleportLoc.clone().add(0.0, -1.0, -1.0),
                    teleportLoc.clone().add(1.0, -1.0, -1.0),
                    teleportLoc.clone().add(-1.0, -1.0, -1.0),
                    teleportLoc.clone().add(-1.0, -1.0, -2.0),
                    teleportLoc.clone().add(1.0, -1.0, -2.0),
                )

                for(it in locations) {
                    if(it.block.type != Material.AIR) continue
                    it.block.type = currentIsland.color.glassMaterial()
//                    it.clone().add(.0, 3.0, .0).block.typ
                }

                val teleportTask = PlatformWaitTask(player, *locations)
                teleportTask.runTaskTimer(gameManager.plugin, 0L, 20L)
            }

            if(item.type == Material.GUNPOWDER){
                if(TeleportTask.teleporting.contains(event.player)){
                    player.sendMessage("&cYou are already teleporting".colorize())
                } else {
                    TeleportTask(gameManager, player).runTaskTimer(gameManager.plugin, 0L, 20L)
                    event.item!!.amount--
                    event.player.updateInventory()
                    event.isCancelled = true
                }
                return
            }

            return
        }

        if(!(item.hasItemMeta())) return
        if(!gameManager.setupWizardManager.isInWizard(event.player)) return
        val island: Island? = gameManager.setupWizardManager.getIsland(player)

        when(itemName){
            "set diamond generator" -> {
                val diamondGenerator = Generator(current, GeneratorType.DIAMOND, false)
                gameManager.configurationManager.saveUnownedGenerator(player.world.name, diamondGenerator)
            }
            "set map centre" -> {
                gameManager.gameConfig.get().set("center.x", current.block.location.x + 0.5)
                gameManager.gameConfig.get().set("center.z", current.block.location.z + 0.5)
                gameManager.gameConfig.save()
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
                    Bukkit.getServer().scheduler.runTaskLater(gameManager.plugin, {->
                            gameManager.setupWizardManager.worldSetupWizard(player, island.gameWorld)
                    }, 4)
                }
            }
            else -> return
        }
        event.isCancelled = true
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent){
        if(event.player.world != gameManager.world.world && gameManager.state == GameState.ACTIVE){
            val playerIsland: Island? = gameManager.world.getIslandForPlayer(event.player)

            event.player.teleport(if(playerIsland == null){
                gameManager.world.lobbyPosition
            } else {
                playerIsland.spawnLocation!!
            })
        }
    }
}
