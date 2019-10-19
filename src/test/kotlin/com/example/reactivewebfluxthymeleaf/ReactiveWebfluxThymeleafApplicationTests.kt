package com.example.reactivewebfluxthymeleaf

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ReactiveWebfluxThymeleafApplicationTests {

    @Test
    fun `well-well! so, hello!`() {
        assertThat("true").isEqualTo("true")
    }
}
