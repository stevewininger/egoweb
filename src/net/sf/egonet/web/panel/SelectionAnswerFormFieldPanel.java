package net.sf.egonet.web.panel;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.html.form.RadioChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.basic.Label;

import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import com.google.common.collect.Lists;

import net.sf.egonet.model.Alter;
import net.sf.egonet.model.Answer;
import net.sf.egonet.model.Question;
import net.sf.egonet.model.QuestionOption;
import net.sf.egonet.persistence.Options;
import net.sf.egonet.web.component.FocusOnLoadBehavior;

public class SelectionAnswerFormFieldPanel extends AnswerFormFieldPanel {

	/**
	 * private inner class extending RadioChoice
	 * This is so it can implement IOnChangeListener and, 
	 * in effect, list to itself and show the 'other specify'
	 * text input box
	 */
	
	private class RadioChoicePlus extends RadioChoice {
		
		public RadioChoicePlus (String id, Model model, List<Object> theList ) {
			super(id, model, theList);
		}
		
		protected void onSelectionChanged(Object newSelection) {
			String strNewSelection;
			
			if ( newSelection instanceof String ) {
				strNewSelection = (String)newSelection;
			} else if ( newSelection instanceof QuestionOption ) {
				strNewSelection = ((QuestionOption)newSelection).getName();
			} else {
				strNewSelection = newSelection.toString();
			}
			
			if (strNewSelection.trim().startsWith(otherSpecify)) {
				otherSpecifyLabel.setVisible(true);
				otherSpecifyTextField.setVisible(true);	
			} else {
				setNotification("");
				otherSpecifyLabel.setVisible(false);
				otherSpecifyTextField.setVisible(false);
			}
		}
		
		protected boolean wantOnSelectionChangedNotifications() { return (otherSpecifyStyle);}
	}
	
	/**
	 * end of private inner class RadioChoicePlus
	 */
	private static final String otherSpecify = "OTHER SPECIFY";
	private Model answer;
	private RadioChoicePlus dropDownChoice;
	private Label otherSpecifyLabel;
	private TextField otherSpecifyTextField;
	private String otherSpecifyText;
	private boolean otherSpecifyStyle;
	
	public SelectionAnswerFormFieldPanel(String id, Question question, ArrayList<Alter> alters, Long interviewId) {
		super(id,question,Answer.SkipReason.NONE,alters, interviewId);
		this.answer = new Model();
		otherSpecifyText = "";
		build();
		setFirstTimeOnQuestion(true);
	}
	
	public SelectionAnswerFormFieldPanel(String id, 
			Question question, String answer, String otherSpecAnswer, Answer.SkipReason skipReason, ArrayList<Alter> alters, Long interviewId) 
	{
		super(id,question,skipReason,alters,interviewId);
		this.answer = new Model();
		otherSpecifyText = otherSpecAnswer;
		if(skipReason.equals(Answer.SkipReason.DONT_KNOW)) {
			this.answer = new Model(dontKnow);
		} else if(skipReason.equals(Answer.SkipReason.REFUSE)) {
			this.answer = new Model(refuse);
		} else {
			try {
				Long optionId = Long.parseLong(answer);
				for(QuestionOption option : getOptions()) {
					if(option.getId().equals(optionId)) {
						this.answer = new Model(option);
					}
				}
			} catch(Exception ex) {
				// Most likely failed to parse answer. Fall back to no existing answer.
			}
		}
		build();
	}
	
	private void build() {
		List<Object> choices = Lists.newArrayList();
		choices.addAll(getOptions());
		if(! question.getType().equals(Question.QuestionType.EGO_ID)) {
			choices.addAll(Lists.newArrayList(dontKnow,refuse));
		}
		dropDownChoice = new RadioChoicePlus("answer",answer,choices);
		dropDownChoice.setPrefix("<span style=\"white-space:nowrap;\">");
		dropDownChoice.setSuffix(
				"</span><span style=\"whitespace:pre-wrap; color:#ffffff\"> . . </span>");
		add(dropDownChoice);
		// features that will be visible only for
		// 'other/specify' questions
		otherSpecifyStyle = question.getOtherSpecify();
		otherSpecifyLabel = new Label("otherSpecifyLabel", "Specify Other: ");
		otherSpecifyTextField = new TextField("otherSpecifyTextField", new PropertyModel(this, "otherSpecifyText"));
		add(otherSpecifyLabel);
		add(otherSpecifyTextField);
		otherSpecifyLabel.setOutputMarkupId(true);
		otherSpecifyTextField.setOutputMarkupId(true);
		
		if ( otherSpecifyStyle && answerContainsOTHERSPECIFY()) {
		    otherSpecifyLabel.setVisible(true);
		    otherSpecifyTextField.setVisible(true);	
		} else {
		    otherSpecifyLabel.setVisible(false);
		    otherSpecifyTextField.setVisible(false);
		}
	}

	public String getAnswer() {
		Object selected = answer.getObject();
		return selected != null && selected instanceof QuestionOption ? 
				((QuestionOption) selected).getId().toString() : null;
	}
	
	public List<QuestionOption> getOptions() {
		return Options.getOptionsForQuestion(getQuestion().getId());
	}

	public void setAutoFocus() {
		dropDownChoice.add(new FocusOnLoadBehavior());
	}

	@Override
	public boolean dontKnow() {
		return answer.getObject() != null && answer.getObject().equals(dontKnow);
	}

	@Override
	public boolean refused() {
		return answer.getObject() != null && answer.getObject().equals(refuse);
	}
	
	private String getAnswerOptionName() {
		Object selected = answer.getObject();
		return selected != null && selected instanceof QuestionOption ? 
				((QuestionOption) selected).getName() : null;	
	}
	
	/**
	 * returns the string used if an incorrect number of checkboxes
	 * are selected to prompt the user to check more or fewer
	 */
	
	public String getRangeCheckNotification() {
		String strNotification = "";
		
		if ( dontKnow() || refused())
			return(strNotification);

		if ( otherSpecifyStyle && otherSpecifyTextField.isVisible() &&
			( otherSpecifyText==null || otherSpecifyText.length()==0 ))
			strNotification = "Specify Other blank";
		return(strNotification);
	}
	
	/** 
	 * if the user selected dontKnow or refused to answer a question
	 * don't bother counting the responses.
	 */
	public boolean rangeCheckOkay() {
		boolean bOkay = true;
		
		if ( dontKnow() || refused())
			return (bOkay);
		
		if ( otherSpecifyStyle && otherSpecifyTextField.isVisible()
			&& ( otherSpecifyText==null || otherSpecifyText.length()==0 ))
			bOkay = false;
		return(bOkay);
	}
	
	
	/**
	 * checks to see if the answer from a previous use
	 * of this question starts with the string OTHER SPECIFY
	 * @return true if a saved answer starts with OTHER SPECIFY
	 */
	private boolean answerContainsOTHERSPECIFY() {
		String strAnswerName = getAnswerOptionName();
		boolean retVal = false;
		
		if ( strAnswerName!=null && strAnswerName.trim().startsWith(otherSpecify)) 
			retVal = true;
		return(retVal);
	 }
	 
	public void setOtherText ( String otherSpecifyText ) {
		this.otherSpecifyText = (otherSpecifyText==null) ? "" : otherSpecifyText;
	}
	public String getOtherText() {
		return (( otherSpecifyText==null) ? "" : otherSpecifyText ) ;
	}

	/**
	 * returns true if strAnswerToCount the answer 
	 * that has been checked.  This will be used an a 
	 * list-of-alters format question to count how many times
	 * a specific answer has been selected
	 * @param strAnswerToCount the answer we'll count up the number
	 * of times its been used
	 * @return true if the answer if selected in this panel
	 */
	public boolean isSelected ( String strAnswerToCount ) {
		String selectedAnswer;
		
		strAnswerToCount = strAnswerToCount.trim();
		selectedAnswer = getAnswerOptionName();
		if (selectedAnswer != null) {
			selectedAnswer = selectedAnswer.trim();
			if ( selectedAnswer.equalsIgnoreCase(strAnswerToCount))
				return (true);
		}
		return (false);
	}	
}
