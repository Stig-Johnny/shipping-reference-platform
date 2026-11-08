package io.github.stigjohnny.shipping.quotes.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A parcel within the contract's bounds, priced by max(weight, ceil(volume / 5000)) grams (invariant Q5). */
class ParcelTest {

    @Test
    void theContractsBoundsAreAccepted() {
        assertThat(new Parcel(1, 1, 1, 1).weightGrams()).isEqualTo(1);
        assertThat(new Parcel(30_000, 1_200, 1_200, 1_200).heightMm()).isEqualTo(1_200);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 1, 1, 1, 'weightGrams must be 1 to 30000, not 0.'",
        "30001, 1, 1, 1, 'weightGrams must be 1 to 30000, not 30001.'",
        "1, 0, 1, 1, 'lengthMm must be 1 to 1200, not 0.'",
        "1, 1201, 1, 1, 'lengthMm must be 1 to 1200, not 1201.'",
        "1, 1, 0, 1, 'widthMm must be 1 to 1200, not 0.'",
        "1, 1, 1201, 1, 'widthMm must be 1 to 1200, not 1201.'",
        "1, 1, 1, 0, 'heightMm must be 1 to 1200, not 0.'",
        "1, 1, 1, 1201, 'heightMm must be 1 to 1200, not 1201.'"
    })
    void outsideTheBoundsAParcelIsRejected(int weight, int length, int width, int height, String message) {
        assertThatThrownBy(() -> new Parcel(weight, length, width, height))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
    }

    @ParameterizedTest
    @CsvSource({
        "300, 200, 100, 1200",
        "10, 10, 10, 1",
        "1, 1, 1, 1",
        "101, 100, 50, 101",
        "101, 101, 50, 103",
        "51, 100, 49, 50",
        "1200, 1200, 1200, 345600"
    })
    void theVolumetricWeightIsTheVolumeOver5000RoundedUp(int length, int width, int height, int grams) {
        assertThat(new Parcel(1, length, width, height).volumetricGrams()).isEqualTo(grams);
    }

    @ParameterizedTest
    @CsvSource({
        "5000, 10, 10, 10, 5000",
        "1200, 600, 400, 200, 9600",
        "1200, 300, 200, 100, 1200",
        "30000, 1200, 1200, 1200, 345600"
    })
    void theBillableWeightIsTheGreaterOfTheWeightAndTheVolumetricWeight(int weight, int length, int width, int height,
            int billable) {
        assertThat(new Parcel(weight, length, width, height).billableWeightGrams()).isEqualTo(billable);
    }
}
