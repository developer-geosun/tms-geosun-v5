package com.geosun.tms.auth.repository;

import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository
    extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

  Optional<User> findByEmailAndDeletedFalse(String email);

  /**
   * Для login: спочатку активний запис з email, інакше видалений (щоб повернути 403 USER_DELETED).
   */
  Optional<User> findTopByEmailOrderByDeletedAsc(String email);

  boolean existsByEmailAndDeletedFalse(String email);

  /** Активний (не видалений) користувач у межах підприємства. */
  Optional<User> findByEnterpriseIdAndEmailAndDeletedFalse(String enterpriseId, String email);

  /** Для login у межах підприємства: спочатку не видалений, інакше видалений. */
  Optional<User> findTopByEnterpriseIdAndEmailOrderByDeletedAsc(String enterpriseId, String email);

  boolean existsByEnterpriseIdAndEmailAndDeletedFalse(String enterpriseId, String email);

  /** Активні ADMIN у межах підприємства (захист останнього адміністратора). */
  @Query(
      """
      select count(distinct u) from User u join u.availableRoles r
      where r = :role and u.active = true and u.deleted = false
        and u.enterpriseId = :enterpriseId
      """)
  long countActiveWithAvailableRoleAndEnterpriseId(
      @Param("role") Role role, @Param("enterpriseId") String enterpriseId);

  boolean existsByEmail(String email);

  @Query(
      """
      select count(distinct u) from User u join u.availableRoles r
      where r = :role and u.deleted = false and u.enterpriseId = :enterpriseId
      """)
  long countNonDeletedWithAvailableRoleAndEnterpriseId(
      @Param("role") Role role, @Param("enterpriseId") String enterpriseId);

  @Query(
      """
      select distinct u from User u join u.availableRoles r
      where r = :role and u.active = true and u.deleted = false
      """)
  List<User> findActiveWithAvailableRole(@Param("role") Role role);

  /** Активні користувачі підприємства з роллю в available (отримувачі сповіщень ADMIN). */
  @Query(
      """
      select distinct u from User u join u.availableRoles r
      where r = :role and u.active = true and u.deleted = false
        and u.enterpriseId = :enterpriseId
      """)
  List<User> findActiveWithAvailableRoleAndEnterpriseId(
      @Param("role") Role role, @Param("enterpriseId") String enterpriseId);

  /** @deprecated використовуйте {@link #findActiveWithAvailableRole(Role)} */
  @Deprecated
  default List<User> findByRoleAndActiveTrueAndDeletedFalse(Role role) {
    return findActiveWithAvailableRole(role);
  }

  @Query(
      """
      select count(distinct u) from User u join u.availableRoles r
      where r = :role and u.active = true and u.deleted = false
      """)
  long countActiveWithAvailableRole(@Param("role") Role role);

  /** Будь-який не видалений користувач з роллю в available (для bootstrap ADMIN). */
  @Query(
      """
      select count(distinct u) from User u join u.availableRoles r
      where r = :role and u.deleted = false
      """)
  long countNonDeletedWithAvailableRole(@Param("role") Role role);

  /** @deprecated використовуйте {@link #countActiveWithAvailableRole(Role)} */
  @Deprecated
  default long countActiveByRole(Role role) {
    return countActiveWithAvailableRole(role);
  }
}
