package dev.flatradar.backend

import dev.flatradar.shared.FeedConfig
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.h2.jdbcx.JdbcDataSource
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedCrudRoutesTest {

    private fun freshDataSource(): DataSource {
        val name = "feedcrudtest${dbCounter.incrementAndGet()}"
        return JdbcDataSource().apply {
            setURL("jdbc:h2:mem:$name;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun feed(id: String = "barmbek") = FeedConfig(
        id = id,
        displayName = "Kleinanzeigen Barmbek",
        url = "https://www.kleinanzeigen.de/s-wohnung-mieten/22297/c203l26487",
        district = "Barmbek",
        source = "kleinanzeigen",
        enabled = true,
    )

    private fun body(feed: FeedConfig) = json.encodeToString(FeedConfig.serializer(), feed)

    @Test
    fun create_then_list_round_trips() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        val created = client.post("/api/v1/feeds") {
            contentType(ContentType.Application.Json)
            setBody(body(feed()))
        }
        assertEquals(HttpStatusCode.Created, created.status)

        val list = client.get("/api/v1/feeds")
        assertEquals(HttpStatusCode.OK, list.status)
        val feeds = json.decodeFromString(ListSerializer(FeedConfig.serializer()), list.bodyAsText())
        assertEquals(1, feeds.size)
        assertEquals("barmbek", feeds[0].id)
        assertEquals("Barmbek", feeds[0].district)
        assertTrue(feeds[0].enabled)
    }

    @Test
    fun posting_same_id_twice_returns_created_then_ok() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        val first = client.post("/api/v1/feeds") {
            contentType(ContentType.Application.Json); setBody(body(feed()))
        }
        assertEquals(HttpStatusCode.Created, first.status)

        val second = client.post("/api/v1/feeds") {
            contentType(ContentType.Application.Json)
            setBody(body(feed().copy(displayName = "renamed")))
        }
        assertEquals(HttpStatusCode.OK, second.status)

        val feeds = json.decodeFromString(
            ListSerializer(FeedConfig.serializer()),
            client.get("/api/v1/feeds").bodyAsText(),
        )
        assertEquals(1, feeds.size)
        assertEquals("renamed", feeds[0].displayName)
    }

    @Test
    fun kleinanzeigen_url_without_c203_is_rejected() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        val res = client.post("/api/v1/feeds") {
            contentType(ContentType.Application.Json)
            setBody(body(feed().copy(url = "https://www.kleinanzeigen.de/s-wohnung-mieten/22297/loose")))
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(res.bodyAsText().contains("c203"))

        // Nothing was persisted.
        val feeds = json.decodeFromString(
            ListSerializer(FeedConfig.serializer()),
            client.get("/api/v1/feeds").bodyAsText(),
        )
        assertTrue(feeds.isEmpty())
    }

    @Test
    fun unknown_source_is_rejected() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        val res = client.post("/api/v1/feeds") {
            contentType(ContentType.Application.Json)
            setBody(body(feed().copy(source = "immoscout"))) // typo for immoscout24
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun immoscout_feed_is_not_c203_checked() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        val res = client.post("/api/v1/feeds") {
            contentType(ContentType.Application.Json)
            setBody(body(feed().copy(id = "is24", source = "immoscout24", url = "https://www.immobilienscout24.de/Suche/x")))
        }
        assertEquals(HttpStatusCode.Created, res.status)
    }

    @Test
    fun put_updates_and_path_id_wins() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        client.post("/api/v1/feeds") {
            contentType(ContentType.Application.Json); setBody(body(feed()))
        }

        // Body carries a different id; the path id ("barmbek") is authoritative.
        val res = client.put("/api/v1/feeds/barmbek") {
            contentType(ContentType.Application.Json)
            setBody(body(feed().copy(id = "ignored", enabled = false)))
        }
        assertEquals(HttpStatusCode.OK, res.status)

        val feeds = json.decodeFromString(
            ListSerializer(FeedConfig.serializer()),
            client.get("/api/v1/feeds").bodyAsText(),
        )
        assertEquals(1, feeds.size)
        assertEquals("barmbek", feeds[0].id)
        assertEquals(false, feeds[0].enabled)
    }

    @Test
    fun delete_removes_the_feed_then_404s() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        client.post("/api/v1/feeds") {
            contentType(ContentType.Application.Json); setBody(body(feed()))
        }

        assertEquals(HttpStatusCode.OK, client.delete("/api/v1/feeds/barmbek").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/v1/feeds/barmbek").status)

        val feeds = json.decodeFromString(
            ListSerializer(FeedConfig.serializer()),
            client.get("/api/v1/feeds").bodyAsText(),
        )
        assertTrue(feeds.isEmpty())
    }

    private companion object {
        val dbCounter = AtomicInteger(0)
        const val TEST_CHANGELOG = "db/changelog/test-changelog.yaml"
    }
}
