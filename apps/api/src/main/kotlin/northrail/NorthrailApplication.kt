package northrail

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NorthrailApplication

fun main(args: Array<String>) {
	runApplication<NorthrailApplication>(*args)
}
