package se.issuetrackingsystem.project.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import se.issuetrackingsystem.user.domain.Admin;
import se.issuetrackingsystem.projectContributor.domain.ProjectContributor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Long id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @OneToMany(mappedBy = "project")
    private List<ProjectContributor> projectContributors = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Project(String title, Admin admin) {
        this.title = title;
        this.admin = admin;
        this.createdAt = LocalDateTime.now();
    }

    public void addContributor(ProjectContributor projectContributor) {
        projectContributors.add(projectContributor);
    }

    public void removeContributor(ProjectContributor projectContributor) {
        projectContributors.remove(projectContributor);
    }
}
