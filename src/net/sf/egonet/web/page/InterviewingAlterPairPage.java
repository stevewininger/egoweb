package net.sf.egonet.web.page;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.Link;

import com.google.common.collect.Lists;

import net.sf.egonet.model.Alter;
import net.sf.egonet.model.Answer;
import net.sf.egonet.model.Question;
import net.sf.egonet.persistence.Answers;
import net.sf.egonet.persistence.Interviewing;
import net.sf.egonet.persistence.Interviews;
import net.sf.egonet.web.panel.AnswerFormFieldPanel;
import net.sf.egonet.web.panel.InterviewingPanel;

import static net.sf.egonet.web.page.InterviewingQuestionIntroPage.possiblyReplaceNextQuestionPageWithPreface;

public class InterviewingAlterPairPage extends InterviewingPage {

	public static class Subject implements Serializable, Comparable<Subject> {
		// TODO: need a way for one of these to represent a question intro: firstAlter -> null
		public Long interviewId;
		public Question question;
		public Alter firstAlter;
		public ArrayList<Alter> secondAlters; // only one alter when not list style, never empty
		public TreeSet<Question> sectionQuestions;
		
		public Alter getSecondAlter() {
			return question.getAskingStyleList() ? null : secondAlters.get(0);
		}
		
		@Override
		public String toString() {
			return question.getType()+" : "+question.getTitle()+" : "+firstAlter.getName()+
			(getSecondAlter() == null ? "" : " : "+getSecondAlter().getName());
		}
		@Override
		public int hashCode() {
			return question.hashCode()+firstAlter.hashCode()+
				(question.getAskingStyleList() ? 0 : getSecondAlter().hashCode());
		}
		@Override
		public boolean equals(Object object) {
			return object instanceof Subject && equals((Subject) object);
		}
		public boolean equals(Subject subject) {
			return interviewId.equals(subject.interviewId) &&
				question.equals(subject.question) &&
				firstAlter.equals(subject.firstAlter) &&
				(question.getAskingStyleList() || getSecondAlter().equals(subject.getSecondAlter()));
		}

		@Override
		public int compareTo(Subject subject) {
			if(sectionQuestions.contains(subject.question)) {
				int firstAlterCompare = firstAlter.compareTo(subject.firstAlter);
				if(firstAlterCompare != 0) {
					return firstAlterCompare;
				}
				boolean list = question.getAskingStyleList() || subject.question.getAskingStyleList();
				int secondAlterCompare = list ? 0 : getSecondAlter().compareTo(subject.getSecondAlter());
				if(secondAlterCompare != 0) {
					return secondAlterCompare;
				}
			}
			return question.compareTo(subject.question);
		}
		
	}

	private Subject subject;
	private InterviewingPanel interviewingPanel;
    private boolean gotoNextUnAnswered;
    
	public InterviewingAlterPairPage(Subject subject) 
	{
		super(subject.interviewId);
		this.subject = subject;
		gotoNextUnAnswered = false;
		build();
		setQuestionId("Question: " + subject.question.getTitle());
	}
	
	private void build() {
		Button nextUnanswered;
		
		Form form = new Form("form") {
			public void onSubmit() {
				onSave(gotoNextUnAnswered);
			}
		};
		
		nextUnanswered = new Button("nextUnanswered") {
			public void onSubmit() {
				gotoNextUnAnswered = true;
			}
		};
		form.add(nextUnanswered);	
		
		ArrayList<AnswerFormFieldPanel> answerFields = Lists.newArrayList();
		for(Alter secondAlter : subject.secondAlters) {
			ArrayList<Alter> alters = Lists.newArrayList(subject.firstAlter,secondAlter);
			Answer answer = 
				Answers.getAnswerForInterviewQuestionAlters(
						Interviews.getInterview(subject.interviewId), 
						subject.question, alters);
			if(answer == null) {
				answerFields.add(
						AnswerFormFieldPanel.getInstance("question", 
								subject.question, alters, subject.interviewId));
			} else {
				answerFields.add(
						AnswerFormFieldPanel.getInstance("question", 
								subject.question, answer.getValue(), answer.getOtherSpecifyText(),
								answer.getSkipReason(), alters, subject.interviewId));
			}
			if(! answerFields.isEmpty()) {
				answerFields.get(0).setAutoFocus();
			}
		}
		 
		interviewingPanel = 
			new InterviewingPanel("interviewingPanel",subject.question,answerFields,subject.interviewId);
		form.add(interviewingPanel);
		
		add(form);
		
		add(new Link("backwardLink") {
			public void onClick() {
				EgonetPage page = 
					askPrevious(subject.interviewId,subject,new InterviewingAlterPairPage(subject));
				if(page != null) {
					setResponsePage(page);
				}
			}
		});
		Link forwardLink = new Link("forwardLink") {
			public void onClick() {
				EgonetPage page = 
					askNext(subject.interviewId,subject,false,new InterviewingAlterPairPage(subject));
				if(page != null) {
					setResponsePage(page);
				}
			}
		};
		add(forwardLink);
		if(! AnswerFormFieldPanel.okayToContinue(answerFields,interviewingPanel.pageFlags())) {
			forwardLink.setVisible(false);
			nextUnanswered.setVisible(false);
		}
	}

	/**
	 * both the "Next Question" and "Next UnAnswered Question" buttons call this
	 * to save the current data and advance
	 * @param gotoNextUnAnswered if true proceed to next UNANSWERED question
	 * if false proceed to next questions
	 */
	public void onSave(boolean gotoNextUnAnswered) {
		List<String> pageFlags = interviewingPanel.pageFlags();
		ArrayList<AnswerFormFieldPanel> answerFields = interviewingPanel.getAnswerFields();
		boolean okayToContinue = 
			AnswerFormFieldPanel.okayToContinue(answerFields, pageFlags);
		boolean consistent = 
			AnswerFormFieldPanel.allConsistent(answerFields, pageFlags);
		boolean multipleSelectionsOkay = 
			AnswerFormFieldPanel.allRangeChecksOkay(answerFields);				
		for(AnswerFormFieldPanel answerField : answerFields) {
			if ( !multipleSelectionsOkay ) {
				answerField.setNotification(answerField.getRangeCheckNotification());
			} else if(okayToContinue) {
				Answers.setAnswerForInterviewQuestionAlters(
						subject.interviewId, subject.question, answerField.getAlters(), 
						answerField.getAnswer(), answerField.getOtherText(),
						answerField.getSkipReason(pageFlags));
			} else if(consistent) {
				answerField.setNotification(
						answerField.answeredOrRefused(pageFlags) ?
								"" : "Unanswered");
			} else {
				answerField.setNotification(
					answerField.consistent(pageFlags) ?
							"" : answerField.inconsistencyReason(pageFlags));
			}
		}
		if(okayToContinue) {
			if (gotoNextUnAnswered) {
				setResponsePage(
						askNext(subject.interviewId,subject,true,new InterviewingAlterPairPage(subject)));
			} else {
				EgonetPage page = 
					askNext(subject.interviewId,subject,false,new InterviewingAlterPairPage(subject));
				if(page != null) {
					setResponsePage(page);	
				}
			}
		}
	}

	public String toString() {
		return subject.toString();
	}
	
	public static EgonetPage askNext(
			Long interviewId, Subject currentPage, boolean unansweredOnly, EgonetPage comeFrom) 
	{
		Subject nextSubject = 
			Interviewing.nextAlterPairPageForInterview(interviewId, currentPage, true, unansweredOnly);
		if(nextSubject != null) {
			EgonetPage nextPage = new InterviewingAlterPairPage(nextSubject);
			return possiblyReplaceNextQuestionPageWithPreface(
					interviewId,nextPage,
					currentPage == null ? null : currentPage.question, 
					nextSubject.question,
					comeFrom,nextPage);
		}
		return new InterviewingConclusionPage(interviewId);
	}
	public static EgonetPage askPrevious(Long interviewId, Subject currentSubject, EgonetPage comeFrom) {
		Subject previousSubject =
			Interviewing.nextAlterPairPageForInterview(interviewId, currentSubject, false, false);
		EgonetPage previousPage = 
			previousSubject != null ? 
					new InterviewingAlterPairPage(previousSubject) : 
						InterviewingAlterPage.askPrevious(interviewId, null, comeFrom);
		return possiblyReplaceNextQuestionPageWithPreface(
				interviewId,previousPage,
				previousSubject == null ? null : previousSubject.question,
				currentSubject == null ? null : currentSubject.question,
				previousPage,comeFrom);
	}
}
