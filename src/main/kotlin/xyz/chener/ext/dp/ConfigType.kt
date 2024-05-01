package xyz.chener.ext.dp

import com.fasterxml.jackson.databind.ObjectMapper
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.parser.ParserImpl
import org.yaml.snakeyaml.scanner.Scanner
import org.yaml.snakeyaml.scanner.ScannerImpl
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties


enum class ConfigType {
    PROPERTIES,YAML,JSON,AUTO
}

class TypeDerivation{
    companion object{
        private fun deriveTypeBySuffix(path:String):ConfigType{
            return when{
                path.endsWith(".properties") -> ConfigType.PROPERTIES
                path.endsWith(".yml") -> ConfigType.YAML
                path.endsWith(".yaml") -> ConfigType.YAML
                path.endsWith(".json") -> ConfigType.JSON
                else -> ConfigType.AUTO
            }
        }

        private fun isValidYaml(content:String):Boolean{
            try {
                val yaml = Yaml()
                yaml.loadAs(content,Map::class.java)
            }catch (_:Exception){
                return false
            }
            return true
        }

        private fun isValidJson(content:String):Boolean{
            try {
                ObjectMapper().readValue<Map<*,*>>(content,Map::class.java)
                return true
            }catch (_:Exception){
                return false
            }
        }

        private fun isValidProperties(content:String):Boolean{
            try {
                val p = Properties()
                p.load(StringReader(content))
                return p.size>0
            }catch (_:Exception){
                return false
            }
        }

        private fun deriveTypeByContent(content:String):ConfigType{
            if(isValidYaml(content)){
                return ConfigType.YAML
            }
            if(isValidJson(content)){
                return ConfigType.JSON
            }
            if(isValidProperties(content)){
                return ConfigType.PROPERTIES
            }
            return ConfigType.AUTO
        }

        fun deriveType(path:String):ConfigType{
            if (!Files.exists(Path.of(path)) || Files.isDirectory(Path.of(path))) {
                throw IllegalArgumentException("File path does not exist or is a directory : $path .")
            }

            val content = Files.readString(Path.of(path))
            val type = deriveTypeBySuffix(path)

            if(type != ConfigType.AUTO){
                val contentType = deriveTypeByContent(content)
                if(contentType != type){
                    throw IllegalArgumentException("The file content and suffix format do not match.")
                }
            }else {
                val contentType = deriveTypeByContent(content)
                if(contentType == ConfigType.AUTO){
                    throw IllegalArgumentException("Unable to automatically derive type.")
                }
                return contentType
            }
            return type
        }
    }
}