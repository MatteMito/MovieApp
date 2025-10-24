export declare class CreateListDto {
    user_id: string;
    name: string;
    description?: string;
    movie_ids?: string[];
    target_date?: string;
    frequency?: string;
    is_public?: boolean;
}
export declare class UpdateListDto {
    name?: string;
    description?: string;
    movie_ids?: string[];
    target_date?: string;
    frequency?: string;
    is_public?: boolean;
}
export declare class AddMovieToListDto {
    movie_id: string;
}
export declare class SearchMovieDto {
    query?: string;
    director?: string;
    genre?: string;
    year?: string;
}
