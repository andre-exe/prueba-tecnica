package com.coworking.reservations.service;

import com.coworking.reservations.domain.entity.Space;
import com.coworking.reservations.domain.enums.SpaceType;
import com.coworking.reservations.dto.request.SpaceRequest;
import com.coworking.reservations.dto.response.SpaceResponse;
import com.coworking.reservations.exception.DuplicateSpaceNameException;
import com.coworking.reservations.exception.ResourceNotFoundException;
import com.coworking.reservations.mapper.SpaceMapper;
import com.coworking.reservations.repository.SpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceServiceImplTest {

    @Mock
    private SpaceRepository spaceRepository;

    private final SpaceMapper spaceMapper = Mappers.getMapper(SpaceMapper.class);

    private SpaceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SpaceServiceImpl(spaceRepository, spaceMapper);
    }

    private SpaceRequest request(String name) {
        return new SpaceRequest(name, SpaceType.MEETING_ROOM, 8, " Piso 2 ", new BigDecimal("15.50"));
    }

    @Test
    void createTrimsFieldsAndReturnsResponse() {
        when(spaceRepository.existsByName("Sala Roble")).thenReturn(false);
        when(spaceRepository.saveAndFlush(any(Space.class))).thenAnswer(inv -> inv.getArgument(0));

        SpaceResponse response = service.create(request("  Sala Roble "));

        assertThat(response.name()).isEqualTo("Sala Roble");
        assertThat(response.location()).isEqualTo("Piso 2");
        assertThat(response.active()).isTrue();
        assertThat(response.hourlyRate()).isEqualByComparingTo("15.50");
    }

    @Test
    void createRejectsDuplicatedName() {
        when(spaceRepository.existsByName("Sala Roble")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("Sala Roble")))
                .isInstanceOf(DuplicateSpaceNameException.class);
        verify(spaceRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsNameUsedByAnotherSpace() {
        UUID id = UUID.randomUUID();
        Space space = new Space("Sala A", SpaceType.HOT_DESK, 4, "Piso 1", BigDecimal.TEN);
        when(spaceRepository.findById(id)).thenReturn(Optional.of(space));
        when(spaceRepository.existsByNameAndIdNot("Sala B", id)).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, request("Sala B")))
                .isInstanceOf(DuplicateSpaceNameException.class);
    }

    @Test
    void updateChangesTheSpace() {
        UUID id = UUID.randomUUID();
        Space space = new Space("Sala A", SpaceType.HOT_DESK, 4, "Piso 1", BigDecimal.TEN);
        when(spaceRepository.findById(id)).thenReturn(Optional.of(space));
        when(spaceRepository.existsByNameAndIdNot("Sala A", id)).thenReturn(false);
        when(spaceRepository.saveAndFlush(space)).thenReturn(space);

        SpaceResponse response = service.update(id, request("Sala A"));

        assertThat(response.type()).isEqualTo(SpaceType.MEETING_ROOM);
        assertThat(response.capacity()).isEqualTo(8);
    }

    @Test
    void deactivateIsALogicalDelete() {
        UUID id = UUID.randomUUID();
        Space space = new Space("Sala A", SpaceType.HOT_DESK, 4, "Piso 1", BigDecimal.TEN);
        when(spaceRepository.findById(id)).thenReturn(Optional.of(space));

        service.deactivate(id);

        assertThat(space.isActive()).isFalse();
        verify(spaceRepository, never()).delete(any(Space.class));
    }

    @Test
    void deactivateFailsWhenSpaceDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(spaceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inactiveSpaceIsHiddenFromRegularUsers() {
        UUID id = UUID.randomUUID();
        Space space = new Space("Sala A", SpaceType.HOT_DESK, 4, "Piso 1", BigDecimal.TEN);
        space.deactivate();
        when(spaceRepository.findById(id)).thenReturn(Optional.of(space));

        assertThatThrownBy(() -> service.findById(id, false)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.findById(id, true).active()).isFalse();
    }
}
