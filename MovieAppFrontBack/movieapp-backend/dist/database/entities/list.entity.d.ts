export declare class MovieListEntity {
    id: string;
    user_id: string;
    name: string;
    description: string;
    movie_ids: string[];
    is_public: boolean;
    follower_ids: string[];
    followers_count: number;
    target_date: Date;
    frequency: string;
    created_at: Date;
    updated_at: Date;
}
