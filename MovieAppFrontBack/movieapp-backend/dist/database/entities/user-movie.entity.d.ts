import { UserEntity } from './user.entity';
import { MovieEntity } from './movie.entity';
export declare enum MovieStatus {
    WATCHED = "watched",
    WATCHLIST = "watchlist"
}
export declare class UserMovieEntity {
    id: string;
    userId: string;
    user: UserEntity;
    movieId: string;
    movie: MovieEntity;
    status: MovieStatus;
    userRating?: number;
    watchedDate?: Date;
    userReview?: string;
    isFavorite?: boolean;
    createdAt: Date;
    updatedAt: Date;
}
