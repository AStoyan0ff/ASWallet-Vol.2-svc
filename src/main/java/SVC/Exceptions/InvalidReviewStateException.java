package SVC.Exceptions;

public class InvalidReviewStateException extends RuntimeException {

    public InvalidReviewStateException(String message) {
        super(message);
    }
}
