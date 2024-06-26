package se.issuetrackingsystem.issue.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import se.issuetrackingsystem.common.exception.CustomException;
import se.issuetrackingsystem.common.exception.ErrorCode;
import se.issuetrackingsystem.issue.domain.Issue;
import se.issuetrackingsystem.issue.dto.IssueStatisticsResponse;
import se.issuetrackingsystem.issue.repository.IssueRepository;
import se.issuetrackingsystem.project.domain.Project;
import se.issuetrackingsystem.project.repository.ProjectRepository;
import se.issuetrackingsystem.user.domain.Dev;
import se.issuetrackingsystem.projectContributor.domain.ProjectContributor;
import se.issuetrackingsystem.user.domain.User;
import se.issuetrackingsystem.projectContributor.repository.ProjectContributorRepository;
import se.issuetrackingsystem.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class IssueService {

    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectContributorRepository projectContributorRepository;

    public Issue create(Long projectId, String title, String description, Long reporterId, Issue.Priority priority){
        Issue issue = new Issue();
        Project project = projectRepository.findById(projectId).orElseThrow(()->new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        User user = userRepository.findById(reporterId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if(!user.canManageIssue()){
            throw new CustomException(ErrorCode.ROLE_FORBIDDEN);
        }
        issue.setProject(project);
        issue.setTitle(title);
        issue.setDescription(description);
        if (priority!=null) {
            issue.setPriority(priority);
        }
        issue.setReporter(user);
        issue.setCreatedAt(LocalDateTime.now());
        issueRepository.save(issue);
        return issue;
    }

    public Issue getIssue(Long issueId){
        return issueRepository.findById(issueId).orElseThrow(() -> new CustomException(ErrorCode.ISSUE_NOT_FOUND));
    }

    public List<Issue> getList(Long projectId){
        List<Issue> issues;
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        issues = issueRepository.findAllByProject(project);
        return issues;
    }

    public List<Issue> getListByAssignee(Long userId){
        List<Issue> issues;
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        issues = issueRepository.findAllByAssignee(user);
        return issues;
    }

    public List<Issue> getList(Long projectId, Issue.Status status){
        List<Issue> issues;
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        issues = issueRepository.findAllByProjectAndStatus(project,status);
        return issues;
    }

    public Issue modify(Long issueId, String title ,String description, Issue.Priority priority,Long userid){
        Issue issue = issueRepository.findById(issueId).orElseThrow(()->new CustomException(ErrorCode.ISSUE_NOT_FOUND));
        User user = userRepository.findById(userid).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if(!user.canManageIssue()){
            throw new CustomException(ErrorCode.ROLE_FORBIDDEN);
        }
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setUpdatedAt(LocalDateTime.now());
        issue.setPriority(priority);
        issueRepository.save(issue);
        return issue;
    }

    public Issue delete(Long issueId,Long userid){
        Issue issue = issueRepository.findById(issueId).orElseThrow(()->new CustomException(ErrorCode.ISSUE_NOT_FOUND));
        User user = userRepository.findById(userid).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if(!user.canManageIssue()){
            throw new CustomException(ErrorCode.ROLE_FORBIDDEN);
        }
        issueRepository.delete(issue);
        return issue;
    }

    public Issue setAssignee(Long issueId, Long userId, Long assigneeId){
        Issue issue = issueRepository.findById(issueId).orElseThrow(()->new CustomException(ErrorCode.ISSUE_NOT_FOUND));
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User assignee = userRepository.findById(assigneeId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.canSetAssignee()||!assignee.canChangeIssueStateAssignedToFixed()) {
            throw new CustomException(ErrorCode.ROLE_BAD_REQUEST);
        }
        issue.setAssignee(assignee);
        issue.setStatus(Issue.Status.ASSIGNED);
        issueRepository.save(issue);
        return issue;
    }

    public Issue changeStatus(Long userId, Long issueId){
        Issue issue = issueRepository.findById(issueId).orElseThrow(()->new CustomException(ErrorCode.ISSUE_NOT_FOUND));
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        switch (issue.getStatus()){
            case NEW :
                throw new CustomException(ErrorCode.METHOD_NOT_ALLOWED);
            case ASSIGNED:
                if(!user.canChangeIssueStateAssignedToFixed()){
                    throw new CustomException(ErrorCode.ROLE_FORBIDDEN);
                }
                else {
                    issue.setStatus(Issue.Status.FIXED);
                    issue.setFixer(user);
                }
                break;
            case FIXED:
                if(!user.canChangeIssueStateFixedToResolved()){
                    throw new CustomException(ErrorCode.ROLE_FORBIDDEN);
                }
                else {
                    issue.setStatus(Issue.Status.RESOLVED);
                }
                break;
            case RESOLVED,REOPENED:
                if (!user.canChangeIssueStateResolvedToClosed()) {
                    throw new CustomException(ErrorCode.ROLE_FORBIDDEN);
                }
                else {
                    issue.setStatus(Issue.Status.CLOSE);
                }
                break;
            case CLOSE:
                if(!user.canChangeIssueStateResolvedToClosed()){
                    throw new CustomException(ErrorCode.ROLE_FORBIDDEN);
                }
                else {
                    issue.setStatus(Issue.Status.REOPENED);
                }
                break;
        }
        issueRepository.save(issue);
        return issue;
    }
    public Optional<User> candidateUser(Long issueId)
    {
        Issue issue = issueRepository.findById(issueId).orElseThrow(()->new CustomException(ErrorCode.ISSUE_NOT_FOUND));
        Project project = issue.getProject();
        List<User> users = userRepository.findAll();
        ArrayList<User> devs = new ArrayList<>();
        ArrayList<User> projectDevs = new ArrayList<>();
        //모든 developer 추가
        for(User u : users){
            if(u instanceof Dev){
                devs.add(u);
            }
        }
        //프로젝트에 할당된 개발자 식별
        List<ProjectContributor> proconts;
        List<Project> projs;
        for(User u : devs){
            projs = new ArrayList<>();
            proconts = projectContributorRepository.findByContributor(u);

            for(ProjectContributor pc : proconts){
                projs.add(pc.getProject());
            }
            if(projs.contains(project)){
                projectDevs.add(u);
            }
        }
        //issue title,description 단어 파싱
        String issueTitle = issue.getTitle();
        ArrayList<String> titleWords = new ArrayList<>(Arrays.asList(issueTitle.split(" ")));
        String issueDesc = issue.getDescription();
        ArrayList<String> descWords = new ArrayList<>(Arrays.asList(issueDesc.split(" ")));

        //식별된 dev에 대한 정보객체 생성
        Integer maxP = 0;
        Optional<User> result = Optional.empty();
        for(User d : projectDevs){
            devInfo tempInfo = new devInfo(d,issueRepository.findAllByFixer(d));
            for(String s : titleWords){
                if(tempInfo.getIssuesTitleWords().containsKey(s)){
                    tempInfo.addPoints(tempInfo.getIssuesTitleWords().get(s)*10);
                }
            }
            for(String s : descWords){
                if(tempInfo.getIssuesDescriptionWords().containsKey(s)){
                    tempInfo.addPoints(tempInfo.getIssuesDescriptionWords().get(s));
                }
            }
            if(tempInfo.getPoints() > maxP){
                maxP= tempInfo.getPoints();
                result=Optional.ofNullable(tempInfo.getUser());
            }
        }
        if(result.isEmpty()){
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return result;
    }

    @Getter
    public class devInfo {
        private User user;
        private ArrayList<Issue> fixedIssues;
        private Map<String, Integer> issuesTitleWords = new HashMap<>();
        private Map<String, Integer> issuesDescriptionWords = new HashMap<>();
        private Integer points;

        devInfo(User dev, List<Issue> issues) {
            points = 0;
            user = dev;

            fixedIssues = new ArrayList<>(issues);
            ArrayList<String> sTemp;
            for (Issue i : fixedIssues) {
                sTemp = new ArrayList<>(Arrays.asList(i.getTitle().split(" ")));
                for (String s : sTemp) {
                    if (issuesTitleWords.containsKey(s)) {
                        issuesTitleWords.put(s, issuesTitleWords.get(s) + 1);
                    } else {
                        issuesTitleWords.put(s, 1);
                    }
                }
            }
            for (Issue i : fixedIssues) {
                sTemp = new ArrayList<>(Arrays.asList(i.getDescription().split(" ")));
                for (String s : sTemp) {
                    if (issuesDescriptionWords.containsKey(s)) {
                        issuesDescriptionWords.put(s, issuesDescriptionWords.get(s) + 1);
                    } else {
                        issuesDescriptionWords.put(s, 1);
                    }
                }
            }
        }

        public void addPoints(Integer value) {
            points += value;
        }
    }
    public IssueStatisticsResponse getIssueStatistics(Long projectId) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        List<Issue> issues = issueRepository.findAllByProject(project);

        // Issue 상태 분포
        Map<Issue.Status, Long> statusDistribution = issues.stream()
                .collect(Collectors.groupingBy(Issue::getStatus, Collectors.counting()));

        // Reporter 분포
        Map<String, Long> reporterDistribution = issues.stream()
                .map(issue -> Optional.ofNullable(issue.getReporter())
                        .map(User::getUsername)
                        .orElse("No Reporter"))
                .collect(Collectors.groupingBy(reporter -> reporter, Collectors.counting()));

        // Assignee 분포
        Map<String, Long> assigneeDistribution = issues.stream()
                .map(issue -> Optional.ofNullable(issue.getAssignee())
                        .map(User::getUsername)
                        .orElse("No Assignee"))
                .collect(Collectors.groupingBy(assignee -> assignee, Collectors.counting()));


        // 댓글 개수 상위 이슈 리스트
        List<String> topCommentedIssues = issues.stream()
                .sorted(Comparator.comparingInt((Issue issue) -> issue.getCommentList().size()).reversed())
                .limit(5) // 필요한 개수만큼 제한
                .map(Issue::getTitle)
                .collect(Collectors.toList());

        return new IssueStatisticsResponse(statusDistribution, reporterDistribution, assigneeDistribution, topCommentedIssues);
    }
}
