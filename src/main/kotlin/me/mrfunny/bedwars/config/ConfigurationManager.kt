package me.mrfunny.bedwars.config

import me.mrfunny.api.CustomConfiguration
import me.mrfunny.bedwars.game.GameManager
import me.mrfunny.bedwars.worlds.GameWorld
import me.mrfunny.bedwars.worlds.generators.Generator
import me.mrfunny.bedwars.worlds.generators.GeneratorType
import me.mrfunny.bedwars.worlds.islands.Island
import me.mrfunny.bedwars.worlds.islands.IslandColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.craftbukkit.libs.org.apache.commons.lang3.EnumUtils
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors
import kotlin.random.Random

class ConfigurationManager(var gameManager: GameManager) {
    private var configuration: ConfigurationSection
    private val config: FileConfiguration

    init {
        config = CustomConfiguration("maps", gameManager.plugin).get()
        if(gameManager.plugin.config.isConfigurationSection("maps")) {
            configuration = gameManager.plugin.config.getConfigurationSection("maps")!!
        } else {
            configuration = gameManager.plugin.config.createSection("maps")
            gameManager.plugin.saveConfig()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun loadWorld(mapName: String, consumer: Consumer<GameWorld>){
        val gameWorld = GameWorld(mapName, gameManager)
        gameWorld.loadWorld(gameManager, true){
            val section: ConfigurationSection = getMapSection(mapName)
            var totalTeams = 0
            for(sectionColor: String in section.getKeys(false)){
                if(EnumUtils.isValidEnum(IslandColor::class.java, sectionColor)) {
                    if (totalTeams < gameWorld.maxTeams) {
                        val island: Island = loadIsland(gameWorld, section.getConfigurationSection(sectionColor) ?: throw RuntimeException(
                            "Invalid island config with $sectionColor"
                        ))
                        for (upgrade in gameManager.upgrades) {
                            island.upgrades.add(upgrade.clone())
                        }
                        totalTeams++
                        gameWorld.islands.add(island)
                    }
                } else {
                    continue
                }
            }

            if(section.isConfigurationSection("generators")){
                val generatorSection: ConfigurationSection = section.getConfigurationSection("generators")!!
                gameWorld.generators = loadGenerators(gameWorld, generatorSection, false) as ArrayList<Generator>
            }

            gameWorld.lobbyPosition = from(gameWorld.world, section.getConfigurationSection("lobbySpawn"))

            consumer.accept(gameWorld)
        }
    }

    fun saveWorld(gameWorld: GameWorld){
        val mapSection = getMapSection(gameWorld.name)
        val lobbySection: ConfigurationSection = if(mapSection.isConfigurationSection("lobbySpawn")){
            mapSection.getConfigurationSection("lobbySpawn")!!
        } else {
            mapSection.createSection("lobbySpawn")
        }

        writeLocation(gameWorld.lobbyPosition, lobbySection)
        gameManager.plugin.saveConfig()
    }

    private fun loadGenerators(world: GameWorld, section: ConfigurationSection?, forIsland: Boolean): MutableList<Generator> {
        if(section == null) return mutableListOf()
        return section.getKeys(false).stream().map { key: String? ->
            if(key == null) return@map null
            val generatorSection = section.getConfigurationSection(key)!!
            val location = from(world.world, generatorSection.getConfigurationSection("location"))

            val typeString = generatorSection.getString("type")

            if (!EnumUtils.isValidEnum(GeneratorType::class.java, typeString)) {
                return@map null
            }
            Generator(location, GeneratorType.valueOf(typeString ?: "IRON"), forIsland)
        }.collect(Collectors.toList())
    }

    private fun getMapSection(worldName: String): ConfigurationSection {
        if(!configuration.isConfigurationSection(worldName)){
            configuration.createSection(worldName)
        }

        return configuration.getConfigurationSection(worldName)!!
    }

    fun randomMapName(): String {
        val mapNames = configuration.getKeys(false).toTypedArray()
        return if(mapNames.size == 1){
            mapNames[0]
        } else {
            mapNames[Random.nextInt(0, mapNames.size)]
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun loadIsland(world: GameWorld, section: ConfigurationSection): Island {
        val color: IslandColor = IslandColor.valueOf(section.name)

        val island = Island(world, color)
        try{
            island.bedLocation = from(world.world, section.getConfigurationSection("bed"))
            island.spawnLocation = from(world.world, section.getConfigurationSection("spawn"))
            island.upgradeEntityLocation = from(world.world, section.getConfigurationSection("upgradeLocation"))
            island.shopEntityLocation = from(world.world, section.getConfigurationSection("shop"))
            island.protectedCorner1 = from(world.world, section.getConfigurationSection("cornerOne"))
            island.protectedCorner2 = from(world.world, section.getConfigurationSection("cornerTwo"))
            island.islandGenerators = loadGenerators(world, section.getConfigurationSection("generators"), true) as ArrayList<Generator>
        } catch (exception: Exception) {
            Bukkit.getLogger().severe("Invalid ${color.formattedName()} island in ${world.name}")
        }

        return island
    }

    fun saveUnownedGenerator(worldConfigName: String, generator: Generator){
        val mapSection: ConfigurationSection = getMapSection(worldConfigName)
        val generatorSection = if(!mapSection.isConfigurationSection("generators")) {
            mapSection.createSection("generators")
        } else {
            mapSection.getConfigurationSection("generators")
        }

        val section: ConfigurationSection = generatorSection!!.createSection(UUID.randomUUID().toString())
        section.set("type", generator.type.toString())
        writeLocation(generator.location, section.createSection("location"))

        gameManager.plugin.saveConfig()
    }

    fun saveIsland(island: Island){

        val mapSection: ConfigurationSection = getMapSection(island.gameWorld.name)

        val colorSection: ConfigurationSection = if(mapSection.isConfigurationSection(island.color.toString())){
            mapSection.getConfigurationSection(island.color.toString())!!
        } else {
            mapSection.createSection(island.color.toString())
        }

        val locationsToWrite = hashMapOf<String, Location?>()

        locationsToWrite["upgradeLocation"] = island.upgradeEntityLocation
        locationsToWrite["bed"] = island.bedLocation
        locationsToWrite["shop"] = island.shopEntityLocation
        locationsToWrite["spawn"] = island.spawnLocation
        locationsToWrite["cornerOne"] = island.protectedCorner1
        locationsToWrite["cornerTwo"] = island.protectedCorner2

        for (it in locationsToWrite.entries) {
            val section: ConfigurationSection = if(!mapSection.isConfigurationSection(it.key)){
                colorSection.createSection(it.key)
            } else {
                colorSection.getConfigurationSection(it.key)!!
            }

            if(it.value != null){
                writeLocation(it.value, section)
            }
        }

        colorSection.set("generators", null)

        val generatorSection: ConfigurationSection = colorSection.createSection("generators")

        for (generator in island.islandGenerators) {
            val section: ConfigurationSection = generatorSection.createSection(UUID.randomUUID().toString())
            section.set("type", generator.type.toString())
            writeLocation(generator.location, section.createSection("location"))
        }

        gameManager.plugin.saveConfig()
    }

    companion object {
        fun from(world: World, section: ConfigurationSection?): Location {
            if(section == null) return Location(world, .0, .0, .0)
            return Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), section.getDouble("yaw").toFloat(), section.getDouble("pitch").toFloat())
        }

        fun writeLocation(location: Location?, section: ConfigurationSection){
            section.set("x", location?.x)
            section.set("y", location?.y)
            section.set("z", location?.z)
            section.set("yaw", location?.yaw)
            section.set("pitch", location?.pitch)
        }
    }

}