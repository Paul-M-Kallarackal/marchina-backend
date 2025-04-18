package com.marchina.marchina.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.marchina.marchina.entity.UserData;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<UserData, Long> {

    // Finder method to select all users
    List<UserData> findAll();
}
