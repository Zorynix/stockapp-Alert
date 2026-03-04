package ru.tuganov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.Notification;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByAppUserOrderByCreatedAtDesc(AppUser appUser);

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
}
