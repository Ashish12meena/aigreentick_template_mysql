package com.apargo.services.template.application.mapper;

import com.apargo.services.template.api.mapper.TemplateDetailResponseMapper;
import com.apargo.services.template.api.request.SyncTemplateRequest;
import com.apargo.services.template.api.response.TemplateDetailResponseDto;
import com.apargo.services.template.application.dto.command.CreateTemplateCommand;
import com.apargo.services.template.ProductCarouselFixtures;
import com.apargo.services.template.common.util.helper.FacebookJsonMapper;
import com.apargo.services.template.common.util.helper.MetaTemplateJson;
import com.apargo.services.template.domain.enums.CardComponentFormat;
import com.apargo.services.template.domain.enums.CarouselButtonType;
import com.apargo.services.template.domain.enums.ComponentType;
import com.apargo.services.template.domain.enums.TemplateCategory;
import com.apargo.services.template.domain.model.WhatsappTemplate;
import com.apargo.services.template.domain.model.WhatsappTemplateCarouselCard;
import com.apargo.services.template.domain.model.WhatsappTemplateCarouselCardComponent;
import com.apargo.services.template.domain.model.WhatsappTemplateComponent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Product card carousels through every mapping: the body we send Meta, a Meta sync, and the
 * detail JSON the Messaging service reads.
 */
class ProductCarouselMappingTest {

    /** Meta's GET returns upper case. Shape from Meta's product-card-carousel doc. */
    private static final String META_PRODUCT_CAROUSEL = """
            {"name":"carousel_template_product_cards_v1","language":"en_US","status":"APPROVED",
             "category":"MARKETING","id":"1473688840035974",
             "components":[
               {"type":"BODY","text":"Rare succulents for sale! {{1}}, tap a plant to see it.",
                "example":{"body_text":[["Pablo"]]}},
               {"type":"CAROUSEL","cards":[
                 {"components":[{"type":"HEADER","format":"PRODUCT"},
                                {"type":"BUTTONS","buttons":[{"type":"SPM","text":"View"}]}]},
                 {"components":[{"type":"HEADER","format":"PRODUCT"},
                                {"type":"BUTTONS","buttons":[{"type":"SPM","text":"View"}]}]}]}]}""";

    private static final String META_MEDIA_CAROUSEL = """
            {"name":"summer_sale","language":"en_US","status":"APPROVED","category":"MARKETING","id":"99",
             "components":[
               {"type":"BODY","text":"Summer sale"},
               {"type":"CAROUSEL","cards":[
                 {"components":[{"type":"HEADER","format":"IMAGE","example":{"header_handle":["https://scontent.example/h1"]}},
                                {"type":"BUTTONS","buttons":[{"type":"QUICK_REPLY","text":"More"},
                                                             {"type":"URL","text":"Shop","url":"https://x.example.com/{{1}}"}]}]}]}]}""";

    private final TemplateSyncMapper syncMapper = new TemplateSyncMapper();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void theBodySentToMetaHasAProductHeaderAndAViewButtonOnly() throws Exception {
        String payload = MetaTemplateJson.write(ProductCarouselFixtures.template(TemplateCategory.MARKETING,
                ProductCarouselFixtures.productCard(), ProductCarouselFixtures.productCard()));

        JsonNode card = json.readTree(payload).path("components").get(1).path("cards").get(0).path("components");
        assertThat(card.get(0).toString()).isEqualTo("{\"type\":\"HEADER\",\"format\":\"PRODUCT\"}");
        assertThat(card.get(1).path("buttons").get(0).toString()).isEqualTo("{\"type\":\"SPM\",\"text\":\"View\"}");
    }

    @Test
    void aCreatedProductCarouselIsStoredWithProductHeadersAndViewButtons() {
        CreateTemplateCommand command = CreateTemplateCommand.builder()
                .templateData(ProductCarouselFixtures.template(TemplateCategory.MARKETING,
                        ProductCarouselFixtures.productCard(), ProductCarouselFixtures.productCard()))
                .wabaId("1433508011972817").projectId(438L).organizationId(5L)
                .build();

        WhatsappTemplate template = new WhatsappTemplateMapper().mapToTemplateEntity("{}", 438L, 5L, command);

        for (WhatsappTemplateCarouselCard card : carousel(template).getCarouselCards()) {
            assertThat(card.getCardComponents().get(0).getFormat()).isEqualTo(CardComponentFormat.PRODUCT);
            assertThat(card.getCardComponents().get(1).getButtons().get(0).getButtonType())
                    .isEqualTo(CarouselButtonType.SPM);
        }
    }

    @Test
    void aMetaProductCarouselSyncsWithItsProductHeadersAndViewButtons() throws Exception {
        WhatsappTemplate template = sync(META_PRODUCT_CAROUSEL);

        WhatsappTemplateComponent carousel = carousel(template);
        assertThat(carousel.getCarouselCards()).hasSize(2);
        for (WhatsappTemplateCarouselCard card : carousel.getCarouselCards()) {
            WhatsappTemplateCarouselCardComponent header = card.getCardComponents().get(0);
            assertThat(header.getFormat()).isEqualTo(CardComponentFormat.PRODUCT);
            assertThat(header.getMediaHandle()).isNull();
            assertThat(card.getCardComponents().get(1).getButtons().get(0).getButtonType())
                    .isEqualTo(CarouselButtonType.SPM);
        }
    }

    @Test
    void theDetailJsonMessagingReadsCarriesProductAndSpm() throws Exception {
        WhatsappTemplate template = sync(META_PRODUCT_CAROUSEL);

        TemplateDetailResponseDto dto = new TemplateDetailResponseMapper()
                .mapToDetailResponse(new TemplateDetailResultMapper().toDetailResult(template));
        JsonNode card = json.valueToTree(dto).path("components").get(1).path("carouselCards").get(0);

        JsonNode header = card.path("cardComponents").get(0);
        assertThat(header.path("componentType").asText()).isEqualTo("HEADER");
        assertThat(header.path("format").asText()).isEqualTo("PRODUCT");
        JsonNode button = card.path("cardComponents").get(1).path("buttons").get(0);
        assertThat(button.path("buttonType").asText()).isEqualTo("SPM");
        assertThat(button.path("text").asText()).isEqualTo("View");
        assertThat(button.path("buttonIndex").asInt()).isZero();
    }

    @Test
    void aMediaCarouselStillSyncsUnchanged() throws Exception {
        WhatsappTemplate template = sync(META_MEDIA_CAROUSEL);

        WhatsappTemplateCarouselCardComponent header = carousel(template).getCarouselCards().get(0).getCardComponents().get(0);
        assertThat(header.getFormat()).isEqualTo(CardComponentFormat.IMAGE);
        assertThat(header.getMediaHandle()).isEqualTo("https://scontent.example/h1");
        assertThat(carousel(template).getCarouselCards().get(0).getCardComponents().get(1).getButtons())
                .extracting(b -> b.getButtonType())
                .containsExactly(CarouselButtonType.QUICK_REPLY, CarouselButtonType.URL);
    }

    @Test
    void aCardButtonTypeWeDoNotModelFailsThatTemplateOnly() throws Exception {
        String catalogOnACard = META_PRODUCT_CAROUSEL.replace("\"SPM\"", "\"CATALOG\"");

        assertThatThrownBy(() -> sync(catalogOnACard))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CATALOG");
    }

    private WhatsappTemplate sync(String metaJson) throws Exception {
        SyncTemplateRequest request = FacebookJsonMapper.mapper()
                .treeToValue(json.readTree(metaJson), SyncTemplateRequest.class);
        return syncMapper.fromFacebookTemplate(request, 438L, 5L, "1433508011972817");
    }

    private static WhatsappTemplateComponent carousel(WhatsappTemplate template) {
        return template.getComponents().stream()
                .filter(c -> c.getComponentType() == ComponentType.CAROUSEL)
                .findFirst().orElseThrow();
    }
}
